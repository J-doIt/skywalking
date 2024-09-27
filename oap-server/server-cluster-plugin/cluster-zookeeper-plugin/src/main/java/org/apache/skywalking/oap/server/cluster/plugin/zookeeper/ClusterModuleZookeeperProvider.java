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

import com.google.common.collect.Lists;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use Zookeeper to manage all instances in SkyWalking cluster.
 * <pre>
 * (使用 Zookeeper 管理 SkyWalking 集群中的所有实例。)
 * </pre>
 */
public class ClusterModuleZookeeperProvider extends ModuleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterModuleZookeeperProvider.class);

    private static final String BASE_PATH = "/skywalking";

    /** 集群模块Zookeeper配置 */
    private final ClusterModuleZookeeperConfig config;
    /** curator-framework.jar 中封装的 ZooKeeper 客户端 */
    private CuratorFramework client;
    /** curator-x-discovery.jar 中封装的 ZooKeeper 服务发现 */
    private ServiceDiscovery<RemoteInstance> serviceDiscovery;
    /** Zookeeper 协调器 */
    private ZookeeperCoordinator coordinator;

    public ClusterModuleZookeeperProvider() {
        super();
        this.config = new ClusterModuleZookeeperConfig();
    }

    @Override
    public String name() {
        return "zookeeper";
    }

    @Override
    public Class module() {
        return ClusterModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    /**
     * 准备阶段方法，
     * 初始化并配置 CuratorFramework 客户端连接至 ZooKeeper，根据配置设定重试策略、ACL（访问控制列表）以及连接参数，
     * 并配置服务发现组件，并启动客户端与服务发现服务。
     * 同时，将 自定义协调器 注册为特定服务的实现。
     * 此方法还会阻塞直到与 ZooKeeper 成功建立连接。
     * 支持使用 Digest 认证模式，并在必要时抛出异常以指示配置错误或服务未提供。
     *
     * @throws ServiceNotProvidedException 如果缺少必要的服务配置或提供者
     * @throws ModuleStartException      在模块启动过程中遇到的异常，如配置错误或初始化失败
     */
    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {

        // 创建重试策略，采用指数退避方式，基于配置的初始等待时间和最大重试次数
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(config.getBaseSleepTimeMs(), config.getMaxRetries());

        // 使用 CuratorFramework 构建器 初始化客户端配置
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                                                                         .retryPolicy(retryPolicy)
                                                                         .connectString(config.getHostPort());

        // 如果启用 ACL
        if (config.isEnableACL()) {
            String authInfo = config.getExpression();
            // 对于 Digest 认证模式，生成对应的认证信息
            if ("digest".equals(config.getSchema())) {
                try {
                    authInfo = DigestAuthenticationProvider.generateDigest(authInfo);
                } catch (NoSuchAlgorithmException e) {
                    throw new ModuleStartException(e.getMessage(), e);
                }
            } else {
                // 如果配置的不是 Digest 模式，则抛出异常，因为当前仅支持 Digest
                throw new ModuleStartException("Support digest schema only.");
            }
            // 定义默认的 ACL 列表，包含完全权限的认证用户和可读权限的任何人
            final List<ACL> acls = Lists.newArrayList();
            acls.add(new ACL(ZooDefs.Perms.ALL, new Id(config.getSchema(), authInfo)));
            acls.add(new ACL(ZooDefs.Perms.READ, ZooDefs.Ids.ANYONE_ID_UNSAFE));

            // 自定义 ACL 提供器，用于设置所有路径的默认 ACL
            ACLProvider provider = new ACLProvider() {
                @Override
                public List<ACL> getDefaultAcl() {
                    return acls;
                }

                @Override
                public List<ACL> getAclForPath(String s) {
                    return acls; // 对所有路径应用相同的 ACL
                }
            };
            // 配置 ACL 提供器和认证信息到 Curator 客户端
            builder.aclProvider(provider);
            builder.authorization(config.getSchema(), config.getExpression().getBytes());
        }

        // 构建并初始化 CuratorFramework 客户端实例
        client = builder.build();

        // 构建ZooKeeper中服务发现的根路径，考虑命名空间
        String path = BASE_PATH + (StringUtil.isEmpty(config.getNamespace()) ? "" : "/" + config.getNamespace());

        // 使用 ServiceDiscoveryBuilder 配置服务发现组件
        // 设置服务实例类型、客户端、基础路径、开启实例监听、自定义序列化器
        serviceDiscovery = ServiceDiscoveryBuilder.builder(RemoteInstance.class) // 服务实例类型
                                                  .client(client) // 客户端
                                                  .basePath(path) // 基础路径
                                                  .watchInstances(true) // 监听实例变化
                                                  .serializer(new SWInstanceSerializer()) // 自定义实例序列化与反序列化
                                                  .build();
        try {
            // 启动Curator客户端并阻塞，直到与ZooKeeper成功建立连接
            client.start();
            client.blockUntilConnected();
            // 启动服务发现服务
            serviceDiscovery.start();

            // 创建并初始化自定义协调器，负责集群注册与查询逻辑
            coordinator = new ZookeeperCoordinator(getManager(), config, serviceDiscovery);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new ModuleStartException(e.getMessage(), e);
        }

        // 注册 自定义协调器 为 ClusterRegister 和 ClusterNodesQuery 服务 的 实现
        this.registerServiceImplementation(ClusterRegister.class, coordinator);
        this.registerServiceImplementation(ClusterNodesQuery.class, coordinator);
    }

    @Override
    public void start() {
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }
}
