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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.handler.grpc.ConfigurationDiscoveryServiceHandler;

/**
 * <pre>
 * 配置发现服务提供者。
 *
 * Provider 的 prepare 阶段：
 *      初始化 配置变更观察者（AgentConfigurationsWatcher）
 * Provider 的 start 阶段：
 *      从 ModuleManager 得到 “配置模块提供者” 的 “动态配置服务（DynamicConfigurationService）”，将 “配置变更观察者” 注册到 “动态配置服务”。
 *      创建 配置发现服务Handler（ConfigurationDiscoveryServiceHandler，是GRPCHandler的实现类）
 *      从 ModuleManager 得到 “共享服务模块提供者” 的 “GrpcHandler注册服务（GRPCHandlerRegister）”，将 “配置发现服务Handler” 注册到 “GrpcHandler注册服务”。
 * </pre>
 */
public class ConfigurationDiscoveryProvider extends ModuleProvider {

    // 配置变更观察者：itemName 是 agentConfigurations
    private AgentConfigurationsWatcher agentConfigurationsWatcher;
    // moduleConfig
    private ConfigurationDiscoveryModuleConfig configurationDiscoveryModuleConfig;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        // 配置发现模块
        return ConfigurationDiscoveryModule.class;
    }

    public ConfigurationDiscoveryProvider() {
        // 初始化 moduleConfig
        configurationDiscoveryModuleConfig = new ConfigurationDiscoveryModuleConfig();
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return configurationDiscoveryModuleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        // Provider 的 prepare 阶段：

        // 初始化 配置变更观察者
        agentConfigurationsWatcher = new AgentConfigurationsWatcher(this);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        // Provider 的 start 阶段：

        // 从 ModuleManager 得到 配置模块提供者 的 动态配置服务
        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        // 将 配置变更观察者（itemName=agentConfigurations） 注册到 动态配置服务
        dynamicConfigurationService.registerConfigChangeWatcher(agentConfigurationsWatcher);

        // Register ConfigurationDiscoveryServiceHandler to process gRPC requests for ConfigurationDiscovery.
        // （注册 ConfigurationDiscoveryServiceHandler 以处理 ConfigurationDiscovery 的 gRPC 请求。）
        // 从 ModuleManager 得到 接收方共享服务模块（receiver-sharing-server）的服务提供者 的 GrpcHandler注册服务
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        // 根据 watcher 和 config，new 一个 GRPCHandler，并注册到 GrpcHandler注册服务
        grpcHandlerRegister.addHandler(new ConfigurationDiscoveryServiceHandler(
            agentConfigurationsWatcher,
            configurationDiscoveryModuleConfig.isDisableMessageDigest()
        ));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        // 该模块的必要模块：configuration 和 receiver-sharing-server
        return new String[] {
            ConfigurationModule.NAME,
            SharingServerModule.NAME
        };
    }
}
