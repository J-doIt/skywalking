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

package org.apache.skywalking.oap.server.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.analysis.ApdexThresholdConfig;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.metrics.ApdexMetrics;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.cache.CacheUpdateTimer;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGroupingRuleWatcher;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointNameGroupingRule4OpenapiWatcher;
import org.apache.skywalking.oap.server.core.logging.LoggingConfigWatcher;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateInitializer;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.oal.rt.DisableOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskMutationService;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.BrowserLogQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.TopNRecordsQueryService;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.SourceReceiverImpl;
import org.apache.skywalking.oap.server.core.storage.PersistenceTimer;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.ttl.DataTTLKeeperTimer;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServerConfig;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;

/**
 * Core module provider includes the recommended and default implementations of {@link CoreModule#services()}. All
 * services with these default implementations are widely used including data receiver, data analysis, streaming
 * process, storage and query.
 *
 * NOTICE. In our experiences, no one should re-implement the core module service implementations, unless we are very
 * familiar with all mechanisms of SkyWalking.
 *
 * <pre>
 * (核心模块提供程序包括 CoreModule.services() 的推荐和默认实现。
 * 所有具有这些默认实现的服务都被广泛使用，包括 数据接收者、数据分析、流式处理、存储和查询。
 *
 * 通知。根据我们的经验，除非我们非常熟悉 SkyWalking 的所有机制，否则没有人应该重新实现核心模块服务实现。)
 * </pre>
 */
public class CoreModuleProvider extends ModuleProvider {

    private final CoreModuleConfig moduleConfig;
    private GRPCServer grpcServer;
    private JettyServer jettyServer;
    private RemoteClientManager remoteClientManager;
    private final AnnotationScan annotationScan;
    private final StorageModels storageModels;
    private final SourceReceiverImpl receiver;
    private ApdexThresholdConfig apdexThresholdConfig;
    private EndpointNameGroupingRuleWatcher endpointNameGroupingRuleWatcher;
    private OALEngineLoaderService oalEngineLoaderService;
    private LoggingConfigWatcher loggingConfigWatcher;
    private EndpointNameGroupingRule4OpenapiWatcher endpointNameGroupingRule4OpenapiWatcher;

    public CoreModuleProvider() {
        super();
        this.moduleConfig = new CoreModuleConfig();
        this.annotationScan = new AnnotationScan();
        this.storageModels = new StorageModels();
        this.receiver = new SourceReceiverImpl();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return CoreModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (moduleConfig.isActiveExtraModelColumns()) {
            DefaultScopeDefine.activeExtraModelColumns();
        }
        EndpointNameGrouping endpointNameGrouping = new EndpointNameGrouping();
        final NamingControl namingControl = new NamingControl(
            moduleConfig.getServiceNameMaxLength(),
            moduleConfig.getInstanceNameMaxLength(),
            moduleConfig.getEndpointNameMaxLength(),
            endpointNameGrouping
        );
        this.registerServiceImplementation(NamingControl.class, namingControl);
        MeterEntity.setNamingControl(namingControl);
        try {
            endpointNameGroupingRuleWatcher = new EndpointNameGroupingRuleWatcher(
                this, endpointNameGrouping);

            if (moduleConfig.isEnableEndpointNameGroupingByOpenapi()) {
                endpointNameGroupingRule4OpenapiWatcher = new EndpointNameGroupingRule4OpenapiWatcher(
                    this, endpointNameGrouping);
            }
        } catch (FileNotFoundException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        try {
            scopeScan.scan();
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        this.registerServiceImplementation(MeterSystem.class, new MeterSystem(getManager()));

        AnnotationScan oalDisable = new AnnotationScan();
        oalDisable.registerListener(DisableRegister.INSTANCE);
        oalDisable.registerListener(new DisableRegister.SingleDisableScanListener());
        try {
            oalDisable.scan();
        } catch (IOException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        if (moduleConfig.isGRPCSslEnabled()) {
            grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(),
                                        moduleConfig.getGRPCSslCertChainPath(),
                                        moduleConfig.getGRPCSslKeyPath(),
                                        null
            );
        } else {
            grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort());
        }
        if (moduleConfig.getMaxConcurrentCallsPerConnection() > 0) {
            grpcServer.setMaxConcurrentCallsPerConnection(moduleConfig.getMaxConcurrentCallsPerConnection());
        }
        if (moduleConfig.getMaxMessageSize() > 0) {
            grpcServer.setMaxMessageSize(moduleConfig.getMaxMessageSize());
        }
        if (moduleConfig.getGRPCThreadPoolQueueSize() > 0) {
            grpcServer.setThreadPoolQueueSize(moduleConfig.getGRPCThreadPoolQueueSize());
        }
        if (moduleConfig.getGRPCThreadPoolSize() > 0) {
            grpcServer.setThreadPoolSize(moduleConfig.getGRPCThreadPoolSize());
        }
        // GRPCServer 初始化
        grpcServer.initialize();

        JettyServerConfig jettyServerConfig = JettyServerConfig.builder()
                                                               .host(moduleConfig.getRestHost())
                                                               .port(moduleConfig.getRestPort())
                                                               .contextPath(moduleConfig.getRestContextPath())
                                                               .jettyIdleTimeOut(moduleConfig.getRestIdleTimeOut())
                                                               .jettyAcceptorPriorityDelta(
                                                                   moduleConfig.getRestAcceptorPriorityDelta())
                                                               .jettyMinThreads(moduleConfig.getRestMinThreads())
                                                               .jettyMaxThreads(moduleConfig.getRestMaxThreads())
                                                               .jettyAcceptQueueSize(
                                                                   moduleConfig.getRestAcceptQueueSize())
                                                               .jettyHttpMaxRequestHeaderSize(
                                                                   moduleConfig.getHttpMaxRequestHeaderSize())
                                                               .build();
        jettyServer = new JettyServer(jettyServerConfig);
        // JettyServer 初始化
        jettyServer.initialize();

        this.registerServiceImplementation(ConfigService.class, new ConfigService(moduleConfig));
        this.registerServiceImplementation(
            DownSamplingConfigService.class, new DownSamplingConfigService(moduleConfig.getDownsampling()));

        // 为这个 CoreModuleProvider 的 2个 服务处理程序注册器 分别 注册 实现
        this.registerServiceImplementation(GRPCHandlerRegister.class, new GRPCHandlerRegisterImpl(grpcServer));
        this.registerServiceImplementation(JettyHandlerRegister.class, new JettyHandlerRegisterImpl(jettyServer));

        this.registerServiceImplementation(IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());

        this.registerServiceImplementation(SourceReceiver.class, receiver);

        WorkerInstancesService instancesService = new WorkerInstancesService();
        this.registerServiceImplementation(IWorkerInstanceGetter.class, instancesService);
        this.registerServiceImplementation(IWorkerInstanceSetter.class, instancesService);

        this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
        this.registerServiceImplementation(ModelCreator.class, storageModels);
        this.registerServiceImplementation(IModelManager.class, storageModels);
        this.registerServiceImplementation(ModelManipulator.class, storageModels);

        this.registerServiceImplementation(
            NetworkAddressAliasCache.class, new NetworkAddressAliasCache(moduleConfig));

        this.registerServiceImplementation(TopologyQueryService.class, new TopologyQueryService(getManager()));
        this.registerServiceImplementation(MetricsMetadataQueryService.class, new MetricsMetadataQueryService());
        this.registerServiceImplementation(MetricsQueryService.class, new MetricsQueryService(getManager()));
        this.registerServiceImplementation(TraceQueryService.class, new TraceQueryService(getManager()));
        this.registerServiceImplementation(BrowserLogQueryService.class, new BrowserLogQueryService(getManager()));
        this.registerServiceImplementation(LogQueryService.class, new LogQueryService(getManager()));
        this.registerServiceImplementation(MetadataQueryService.class, new MetadataQueryService(getManager()));
        this.registerServiceImplementation(AggregationQueryService.class, new AggregationQueryService(getManager()));
        this.registerServiceImplementation(AlarmQueryService.class, new AlarmQueryService(getManager()));
        this.registerServiceImplementation(TopNRecordsQueryService.class, new TopNRecordsQueryService(getManager()));
        this.registerServiceImplementation(EventQueryService.class, new EventQueryService(getManager()));

        // add profile service implementations
        this.registerServiceImplementation(
            ProfileTaskMutationService.class, new ProfileTaskMutationService(getManager()));
        this.registerServiceImplementation(
            ProfileTaskQueryService.class, new ProfileTaskQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(ProfileTaskCache.class, new ProfileTaskCache(getManager(), moduleConfig));

        this.registerServiceImplementation(CommandService.class, new CommandService(getManager()));

        // add oal engine loader service implementations
        oalEngineLoaderService = new OALEngineLoaderService(getManager());
        this.registerServiceImplementation(OALEngineLoaderService.class, oalEngineLoaderService);

        annotationScan.registerListener(new StreamAnnotationListener(getManager()));

        if (moduleConfig.isGRPCSslEnabled()) {
            this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout(),
                                                               moduleConfig.getGRPCSslTrustedCAPath()
            );
        } else {
            this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout());
        }
        this.registerServiceImplementation(RemoteClientManager.class, remoteClientManager);

        // Management
        this.registerServiceImplementation(
            UITemplateManagementService.class, new UITemplateManagementService(getManager()));

        if (moduleConfig.getMetricsDataTTL() < 2) {
            throw new ModuleStartException(
                "Metric TTL should be at least 2 days, current value is " + moduleConfig.getMetricsDataTTL());
        }
        if (moduleConfig.getRecordDataTTL() < 2) {
            throw new ModuleStartException(
                "Record TTL should be at least 2 days, current value is " + moduleConfig.getRecordDataTTL());
        }

        final MetricsStreamProcessor metricsStreamProcessor = MetricsStreamProcessor.getInstance();
        metricsStreamProcessor.setEnableDatabaseSession(moduleConfig.isEnableDatabaseSession());
        metricsStreamProcessor.setL1FlushPeriod(moduleConfig.getL1FlushPeriod());
        metricsStreamProcessor.setStorageSessionTimeout(moduleConfig.getStorageSessionTimeout());
        metricsStreamProcessor.setMetricsDataTTL(moduleConfig.getMetricsDataTTL());
        TopNStreamProcessor.getInstance().setTopNWorkerReportCycle(moduleConfig.getTopNReportPeriod());
        apdexThresholdConfig = new ApdexThresholdConfig(this);
        ApdexMetrics.setDICT(apdexThresholdConfig);
        loggingConfigWatcher = new LoggingConfigWatcher(this);
    }

    @Override
    public void start() throws ModuleStartException {

        // 向gRPC服务器添加处理器，用于处理远程服务请求
        grpcServer.addHandler(new RemoteServiceHandler(getManager())); // 添加处理远程服务调用的处理器
        grpcServer.addHandler(new HealthCheckServiceHandler()); // 添加健康检查服务处理器

        // 启动远程客户端管理器，以便管理与其它服务的连接和通信
        remoteClientManager.start();

        // 加载OAL脚本，特别地，禁用OAL脚本具有更高的优先级
        // DisableOALDefine.INSTANCE 通常用于指示不执行任何OAL脚本的情况
        oalEngineLoaderService.load(DisableOALDefine.INSTANCE);

        try {
            // 执行扫描操作，这可能涉及到例如检测或初始化接收器配置、注解解析等功能
            receiver.scan(); // 扫描并处理接收器相关配置或初始化
            annotationScan.scan(); // 扫描并处理应用程序中的注解，用于动态追踪或配置
        } catch (IOException | IllegalAccessException | InstantiationException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        // 初始化gRPC服务器实例地址，包含主机地址、端口以及是否SSL连接的标记
        Address gRPCServerInstanceAddress = new Address(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(), true);
        // 设置 Telemetry上下文ID 为 gRPC服务器实例地址 的字符串表示形式
        TelemetryRelatedContext.INSTANCE.setId(gRPCServerInstanceAddress.toString());
        // 根据模块配置的角色决定是否注册gRPC服务器实例到集群中
        if (CoreModuleConfig.Role.Mixed.name()
                                       .equalsIgnoreCase(
                                           moduleConfig.getRole())
            || CoreModuleConfig.Role.Aggregator.name()
                                               .equalsIgnoreCase(
                                                   moduleConfig.getRole())) {

            // 创建gRPC服务器远程实例
            RemoteInstance gRPCServerInstance = new RemoteInstance(gRPCServerInstanceAddress);
            // 从 模块管理器 中找到 cluster 模块，获取 ClusterRegister 服务，并注册gRPC服务器实例到集群中
            this.getManager()
                .find(ClusterModule.NAME) // 查找名为Cluster的模块
                .provider() // 获取提供者
                .getService(ClusterRegister.class) // 获取ClusterRegister服务实例
                .registerRemote(gRPCServerInstance); // 执行注册操作
        }

        // 根据配置设置OAP节点角色
        OAPNodeChecker.setROLE(CoreModuleConfig.Role.fromName(moduleConfig.getRole()));

        // 获取动态配置服务，并注册配置变化监听器
        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        // 注册Apdex阈值配置监听器
        dynamicConfigurationService.registerConfigChangeWatcher(apdexThresholdConfig);
        // 注册端点名称分组规则监听器
        dynamicConfigurationService.registerConfigChangeWatcher(endpointNameGroupingRuleWatcher);
        // 注册日志配置监听器
        dynamicConfigurationService.registerConfigChangeWatcher(loggingConfigWatcher);
        // 如果启用基于OpenAPI的端点名称分组，则注册相应的监听器
        if (moduleConfig.isEnableEndpointNameGroupingByOpenapi()) {
            dynamicConfigurationService.registerConfigChangeWatcher(endpointNameGroupingRule4OpenapiWatcher);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        try {
            // GRPCServer 启动
            grpcServer.start();
            jettyServer.start();
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        PersistenceTimer.INSTANCE.start(getManager(), moduleConfig);

        if (moduleConfig.isEnableDataKeeperExecutor()) {
            DataTTLKeeperTimer.INSTANCE.start(getManager(), moduleConfig);
        }

        CacheUpdateTimer.INSTANCE.start(getManager(), moduleConfig.getMetricsDataTTL());

        try {
            final File[] templateFiles = ResourceUtils.getPathFiles("ui-initialized-templates");
            for (final File templateFile : templateFiles) {
                if (!templateFile.getName().endsWith(".yml") && !templateFile.getName().endsWith(".yaml")) {
                    continue;
                }
                new UITemplateInitializer(new FileInputStream(templateFile))
                    .read()
                    .forEach(uiTemplate -> {
                        ManagementStreamProcessor.getInstance().in(uiTemplate);
                    });
            }

        } catch (FileNotFoundException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            ConfigurationModule.NAME
        };
    }
}
