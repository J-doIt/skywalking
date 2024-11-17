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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.auth.AuthenticationInterceptor;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServerConfig;

/**
 * <pre>
 * 共享服务模块提供者。
 *
 * 初始化并启动 grpc服务 和 jetty服务。
 * 若 该模块提供者没有设置 grpc服务 和 jetty服务 的 真实端口，则在 Provider 的 start 阶段，拿到 CoreModule 的 GRPCHandlerRegister 和 JettyHandlerRegister 供自己使用。
 * </pre>
 */
public class SharingServerModuleProvider extends ModuleProvider {

    /** moduleConfig */
    private final SharingServerConfig config;
    /**
     * grpc服务，
     * 不为空时，GRPCHandlerRegister 的实现类是 GRPCHandlerRegisterImpl。
     */
    private GRPCServer grpcServer;
    /**
     * jetty服务，
     * 不为空时，JettyHandlerRegister 的实现类是 JettyHandlerRegisterImpl。
     */
    private JettyServer jettyServer;
    /** this.grpcServer 为空时，作为 GRPCHandlerRegister 的实现类 */
    private ReceiverGRPCHandlerRegister receiverGRPCHandlerRegister;
    /** this.jettyServer 为空时，作为 JettyHandlerRegister 的实现类 */
    private ReceiverJettyHandlerRegister receiverJettyHandlerRegister;
    /** grpc令牌检查器（grpc 的 ServerInterceptor 实现类） */
    private AuthenticationInterceptor authenticationInterceptor;

    public SharingServerModuleProvider() {
        super();
        // 初始化 moduleConfig
        this.config = new SharingServerConfig();
    }

    @Override
    public String name() {
        // 共享服务模块提供者名
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        // 共享服务模块
        return SharingServerModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        // 返回 初始化好的 moduleConfig
        return config;
    }

    @Override
    public void prepare() {
        // Provider 的 prepare 阶段：

        // 如果是jetty的真实端口
        if (config.getRestPort() > 0) {
            // 根据 moduleConfig 构建 JettyServerConfig
            JettyServerConfig jettyServerConfig =
                JettyServerConfig.builder()
                                 .host(config.getRestHost()).port(config.getRestPort())
                                 .contextPath(config.getRestContextPath())
                                 .jettyMinThreads(config.getRestMinThreads())
                                 .jettyMaxThreads(config.getRestMaxThreads())
                                 .jettyAcceptQueueSize(config.getRestAcceptQueueSize())
                                 .jettyAcceptorPriorityDelta(
                                     config.getRestAcceptorPriorityDelta())
                                 .jettyIdleTimeOut(config.getRestIdleTimeOut())
                                 .jettyHttpMaxRequestHeaderSize(config.getHttpMaxRequestHeaderSize()).build();
            jettyServerConfig.setHost(Strings.isBlank(config.getRestHost()) ? "0.0.0.0" : config.getRestHost());
            jettyServerConfig.setPort(config.getRestPort());
            jettyServerConfig.setContextPath(config.getRestContextPath());

            // 根据 JettyServerConfig 创建 JettyServer
            jettyServer = new JettyServer(jettyServerConfig);
            // 初始化 JettyServer
            jettyServer.initialize();

            // 为 "共享服务模块" 的 默认提供者 注册 JettyHandlerRegister 的实现类（JettyHandlerRegisterImpl）
            this.registerServiceImplementation(JettyHandlerRegister.class, new JettyHandlerRegisterImpl(jettyServer));
        } else {
            this.receiverJettyHandlerRegister = new ReceiverJettyHandlerRegister();
            // 为 "共享服务模块" 的 默认提供者 注册 JettyHandlerRegister 的实现类（ReceiverJettyHandlerRegister）
            this.registerServiceImplementation(JettyHandlerRegister.class, receiverJettyHandlerRegister);
        }

        // 如果 用于gRPC连接的身份验证令牌 不为空
        if (StringUtil.isNotEmpty(config.getAuthentication())) {
            // 创建 grpc令牌检查器（grpc 的 ServerInterceptor 实现类）
            authenticationInterceptor = new AuthenticationInterceptor(config.getAuthentication());
        }

        // 如果是grpc的真实端口
        if (config.getGRPCPort() != 0) {
            // 如果需要为gRPC服务激活SSL
            if (config.isGRPCSslEnabled()) {
                // 创建 GRPCServer，并激活SSL
                grpcServer = new GRPCServer(
                    Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(),
                    config.getGRPCPort(),
                    config.getGRPCSslCertChainPath(),
                    config.getGRPCSslKeyPath(),
                    config.getGRPCSslTrustedCAsPath()
                );
            } else {
                // 创建 GRPCServer
                grpcServer = new GRPCServer(
                    Strings.isBlank(config.getGRPCHost()) ? "0.0.0.0" : config.getGRPCHost(),
                    config.getGRPCPort()
                );
            }
            if (config.getMaxMessageSize() > 0) {
                grpcServer.setMaxMessageSize(config.getMaxMessageSize());
            }
            if (config.getMaxConcurrentCallsPerConnection() > 0) {
                grpcServer.setMaxConcurrentCallsPerConnection(config.getMaxConcurrentCallsPerConnection());
            }
            if (config.getGRPCThreadPoolQueueSize() > 0) {
                grpcServer.setThreadPoolQueueSize(config.getGRPCThreadPoolQueueSize());
            }
            if (config.getGRPCThreadPoolSize() > 0) {
                grpcServer.setThreadPoolSize(config.getGRPCThreadPoolSize());
            }
            // 初始化 GRPCServer
            grpcServer.initialize();

            GRPCHandlerRegisterImpl grpcHandlerRegister = new GRPCHandlerRegisterImpl(grpcServer);
            // 如果 grpc令牌检查器 不为空
            if (Objects.nonNull(authenticationInterceptor)) {
                // 将 grpc令牌检查器 作为 过滤器 添加到 GRPCHandlerRegisterImpl
                grpcHandlerRegister.addFilter(authenticationInterceptor);
            }
            // 为 "共享服务模块" 的 默认提供者 注册 GRPCHandlerRegister 的实现类（GRPCHandlerRegisterImpl）
            this.registerServiceImplementation(GRPCHandlerRegister.class, grpcHandlerRegister);
        } else {
            this.receiverGRPCHandlerRegister = new ReceiverGRPCHandlerRegister();
            if (Objects.nonNull(authenticationInterceptor)) {
                receiverGRPCHandlerRegister.addFilter(authenticationInterceptor);
            }
            // 为 "共享服务模块" 的 默认提供者 注册 GRPCHandlerRegister 的实现类（ReceiverGRPCHandlerRegister）
            this.registerServiceImplementation(GRPCHandlerRegister.class, receiverGRPCHandlerRegister);
        }
    }

    @Override
    public void start() {
        // Provider 的 start 阶段：

        // 如果 grpc服务 不为空
        if (Objects.nonNull(grpcServer)) {
            // 为 grpc服务 添加 GRPCHandler（健康检查服务Handler）
            grpcServer.addHandler(new HealthCheckServiceHandler());
        }

        if (Objects.nonNull(receiverGRPCHandlerRegister)) {
            // 从 ModuleManager 获取 核心模块提供者 的 GRPCHandlerRegister 实现类，并设置给 this.receiverGRPCHandlerRegister
            receiverGRPCHandlerRegister.setGrpcHandlerRegister(getManager().find(CoreModule.NAME)
                                                                           .provider()
                                                                           .getService(GRPCHandlerRegister.class));
        }
        if (Objects.nonNull(receiverJettyHandlerRegister)) {
            // 从 ModuleManager 获取 核心模块提供者 的 JettyHandlerRegister 实现类，并设置给 this.receiverJettyHandlerRegister
            receiverJettyHandlerRegister.setJettyHandlerRegister(getManager().find(CoreModule.NAME)
                                                                             .provider()
                                                                             .getService(JettyHandlerRegister.class));
        }
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        // Provider 的 start 阶段结束后：

        try {
            if (Objects.nonNull(grpcServer)) {
                // 启动 GRPCServer
                grpcServer.start();
            }
            if (Objects.nonNull(jettyServer)) {
                // 启动 JettyServer
                jettyServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        // 该模块提供者的必要模块：
        return new String[] {CoreModule.NAME};
    }
}
