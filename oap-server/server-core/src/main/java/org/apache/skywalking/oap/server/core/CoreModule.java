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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
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
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Core module definition. Define all open services to other modules.
 * <pre>
 * (核心模块定义。定义所有 开放服务 给其他模块。)
 *
 * CoreModule 类是 SkyWalking 核心模块的定义，它继承自 ModuleDefine，负责定义该模块的基本信息和服务接口列表。
 * </pre>
 */
public class CoreModule extends ModuleDefine {
    public static final String NAME = "core";

    public CoreModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        List<Class> classes = new ArrayList<>();

        // 添加基本服务接口

        classes.add(ConfigService.class); // 配置服务
        classes.add(DownSamplingConfigService.class); // 下采样配置服务
        classes.add(NamingControl.class); // 命名控制服务
        classes.add(IComponentLibraryCatalogService.class); // 组件库目录服务

        // 工作实例管理接口

        classes.add(IWorkerInstanceGetter.class); // 工作实例获取器
        classes.add(IWorkerInstanceSetter.class); // 工作实例设置器

        classes.add(MeterSystem.class); // 度量系统服务

        // 添加更多服务类别，通过调用专门的方法来组织不同的服务接口

        addServerInterface(classes); //  添加服务实例：GRPCHandlerRegister，JettyHandlerRegister
        addReceiverInterface(classes); // 添加接收实例：SourceReceiver
        addInsideService(classes); // 添加内部实例
        addCacheService(classes); // 添加缓存实例：NetworkAddressAliasCache
        addQueryService(classes); // 添加查询实例
        addProfileService(classes); //
        addOALService(classes); // 添加 OAL引擎 实例：OALEngineLoaderService
        addManagementService(classes); // 添加：UITemplateManagementService

        classes.add(CommandService.class); // 命令服务接口

        return classes.toArray(new Class[]{});
    }

    private void addManagementService(List<Class> classes) {
        classes.add(UITemplateManagementService.class);
    }

    /**
     * 添加与性能剖析任务相关的服务到服务列表中。
     * 包括任务的增删改查服务、缓存服务等。
     */
    private void addProfileService(List<Class> classes) {
        classes.add(ProfileTaskMutationService.class); // 性能剖析任务的变更服务
        classes.add(ProfileTaskQueryService.class); // 性能剖析任务的查询服务
        classes.add(ProfileTaskCache.class); // 性能剖析任务的缓存服务
    }

    /**
     * 添加OAL（Observability Analysis Language）引擎加载服务到服务列表。
     * OAL用于定义和执行观测分析逻辑。
     *
     * @param classes 用于存储服务类的列表。
     */
    private void addOALService(List<Class> classes) {
        // SkyWalking后端分析平台 的 OAL（描述分析过程的可扩展、轻量级编译型语言）
        classes.add(OALEngineLoaderService.class); // OAL引擎加载服务
    }

    /**
     * 添加查询服务到列表中，包括拓扑、指标元数据、指标数据、链路追踪、日志、元数据、聚合查询等服务。
     * 这些服务支持从SkyWalking后端查询各种监控和分析数据。
     *
     * @param classes 用于存储服务类的列表。
     */
    private void addQueryService(List<Class> classes) {
        classes.add(TopologyQueryService.class);         // 拓扑查询服务
        classes.add(MetricsMetadataQueryService.class); // 指标元数据查询服务
        classes.add(MetricsQueryService.class);         // 指标数据查询服务
        classes.add(TraceQueryService.class);           // 链路追踪查询服务
        classes.add(LogQueryService.class);             // 日志查询服务
        classes.add(MetadataQueryService.class);        // 元数据查询服务
        classes.add(AggregationQueryService.class);     // 聚合查询服务
        classes.add(AlarmQueryService.class);           // 告警查询服务
        classes.add(TopNRecordsQueryService.class);     // TopN记录查询服务
        classes.add(BrowserLogQueryService.class);      // 浏览器日志查询服务
        classes.add(EventQueryService.class);           // 事件查询服务
    }

    /**
     * 添加服务端接口服务到列表中，涉及 gRPC 和 Jetty 处理程序的注册服务。
     * 这些服务用于处理来自客户端的请求。
     *
     * @param classes 用于存储服务类的列表。
     */
    private void addServerInterface(List<Class> classes) {
        classes.add(GRPCHandlerRegister.class); // gRPC处理程序注册服务
        classes.add(JettyHandlerRegister.class); // Jetty处理程序注册服务
    }

    /**
     * 添加内部服务到列表中，这些服务主要用于框架内部操作和管理。
     * 包括模型创建、模型管理、模型操作、远程客户端管理以及消息发送服务等。
     *
     * @param classes 用于存储服务类的列表。
     */
    private void addInsideService(List<Class> classes) {
        classes.add(ModelCreator.class);          // 模块创建服务
        classes.add(IModelManager.class);         // 模块管理接口实现
        classes.add(ModelManipulator.class);      // 模块操作服务
        classes.add(RemoteClientManager.class);   // 远程客户端管理服务
        classes.add(RemoteSenderService.class);   // 远程消息发送服务
    }

    private void addCacheService(List<Class> classes) {
        classes.add(NetworkAddressAliasCache.class);
    }

    private void addReceiverInterface(List<Class> classes) {
        classes.add(SourceReceiver.class);
    }
}
