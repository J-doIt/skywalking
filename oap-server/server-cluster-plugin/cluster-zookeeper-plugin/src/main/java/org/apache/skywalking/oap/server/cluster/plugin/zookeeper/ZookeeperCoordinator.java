/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Zookeeper 协调器
 */
public class ZookeeperCoordinator implements ClusterRegister, ClusterNodesQuery {

    private static final String REMOTE_NAME_PATH = "remote";

    private final ModuleDefineHolder manager;
    private final ClusterModuleZookeeperConfig config;
    /** curator-x-discovery.jar 中封装的 ZooKeeper 服务发现 */
    private final ServiceDiscovery<RemoteInstance> serviceDiscovery;
    /**
     * 本地缓存 服务发现 的结果，
     * 它的主要职责是跟踪和更新来自 ZooKeeper 的服务实例信息，并在本地维护一份最新的服务实例缓存。
     */
    private final ServiceCache<RemoteInstance> serviceCache;
    /** 当前服务的地址 */
    private volatile Address selfAddress;
    /** 运行状况检查指标 */
    private HealthCheckMetrics healthChecker;

    ZookeeperCoordinator(final ModuleDefineHolder manager, final ClusterModuleZookeeperConfig config,
                         final ServiceDiscovery<RemoteInstance> serviceDiscovery) throws Exception {
        this.manager = manager;
        this.config = config;
        this.serviceDiscovery = serviceDiscovery;
        this.serviceCache = serviceDiscovery.serviceCacheBuilder().name(REMOTE_NAME_PATH).build();
        this.serviceCache.start();
    }

    /**
     * 注册远程服务实例到服务发现中心。此方法为同步操作，以确保实例注册的原子性。
     * 在注册之前，会检查是否需要使用内部通信地址，并据此准备要注册的实例信息。
     * 同时，初始化健康检查器，并在注册成功后触发一次健康检查。
     *
     * @param remoteInstance 要注册的远程服务实例
     * @throws ServiceRegisterException 如果在注册服务实例过程中发生错误，如网络问题或服务发现组件异常
     */
    @Override
    public synchronized void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
            // 初始化健康检查器，用于后续的健康状态监控
            initHealthChecker();

            // 判断是否需要使用内部通信地址，如果是，则替换远程实例的地址信息
            if (needUsingInternalAddr()) {
                remoteInstance = new RemoteInstance(new Address(config.getInternalComHost(), config.getInternalComPort(), true));
            }

            // 使用 ServiceInstance 构建器 创建待注册的服务实例描述
            // 包含服务名称、唯一ID、主机地址、端口号以及原始远程实例作为有效载荷
            ServiceInstance<RemoteInstance> thisInstance = ServiceInstance.<RemoteInstance>builder().name(REMOTE_NAME_PATH)
                                                                                                    .id(UUID.randomUUID()
                                                                                                            .toString()) // 生成唯一ID
                                                                                                    .address(remoteInstance
                                                                                                                 .getAddress()
                                                                                                                 .getHost())
                                                                                                    .port(remoteInstance
                                                                                                              .getAddress()
                                                                                                              .getPort())
                                                                                                    .payload(remoteInstance) // 将原远程实例设为 payload
                                                                                                    .build();

            // 将构造好的服务实例注册到服务发现组件中
            serviceDiscovery.registerService(thisInstance);

            // 更新当前服务实例的自我地址信息
            this.selfAddress = remoteInstance.getAddress();
            // 触发健康检查，表明该服务实例已准备好并处于可服务状态
            this.healthChecker.health();
        } catch (Throwable e) {
            // 服务不可用
            this.healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }

    /**
     * 查询所有远程节点实例列表。此方法首先尝试初始化健康检查器，
     * 然后从缓存中获取所有已注册的服务实例，并转换为RemoteInstance列表。
     * 对于每个实例，会根据其地址信息标记是否为自己（本机）的实例。
     * 接着，检查这些远程实例的运行状况，并据此更新健康检查状态。
     * 如果在查询过程中遇到任何异常，则会记录不健康状态并重新抛出异常。
     *
     * @return List<RemoteInstance> 所有远程节点实例的列表，包含它们的健康状态信息
     * @throws ServiceQueryException 在查询远程节点时遇到不可恢复的错误时抛出此异常
     */
    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>(20);
        try {
            // 确保健康检查器已被初始化
            initHealthChecker();
            // 从服务缓存中获取所有已注册的服务实例
            List<ServiceInstance<RemoteInstance>> serviceInstances = serviceCache.getInstances();
            // 遍历服务实例列表，将每个实例的 payload 转换为 RemoteInstance 并添加到结果列表中
            serviceInstances.forEach(serviceInstance -> {
                RemoteInstance instance = serviceInstance.getPayload();
                // 根据地址信息设置是否为本机标识
                if (instance.getAddress().equals(selfAddress)) {
                    instance.getAddress().setSelf(true);
                } else {
                    instance.getAddress().setSelf(false);
                }
                remoteInstances.add(instance);
            });
            // 检查所有远程实例的运行状况
            ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
            // 根据健康检查结果更新健康检查器的状态
            if (healthStatus.isHealth()) {
                // 标记服务为健康状态
                this.healthChecker.health();
            } else {
                // 标记服务为不健康，并记录不健康原因
                this.healthChecker.unHealth(healthStatus.getReason());
            }
        } catch (Throwable e) {
            // 标记服务为不健康，并传递异常原因
            this.healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }
        return remoteInstances;
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge("cluster_zookeeper", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }
}
