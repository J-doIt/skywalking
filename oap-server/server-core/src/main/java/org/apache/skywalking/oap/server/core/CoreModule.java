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

        classes.add(ConfigService.class); // 配置信息获取服务
        classes.add(DownSamplingConfigService.class); // 下采样聚合级别配置服务
        classes.add(NamingControl.class); // 命名控制服务
        classes.add(IComponentLibraryCatalogService.class); // 组件库登记服务

        classes.add(IWorkerInstanceGetter.class); // Worker实例查找接口
        classes.add(IWorkerInstanceSetter.class); // Worker实例注册接口

        classes.add(MeterSystem.class); // 指标的流处理系统

        addServerInterface(classes); //  GRPCHandlerRegister，JettyHandlerRegister
        addReceiverInterface(classes); // SourceReceiver
        addInsideService(classes); // ModelCreator、IModelManager、ModelManipulator、RemoteClientManager、RemoteSenderService
        addCacheService(classes); // NetworkAddressAliasCache
        addQueryService(classes); // 各种查询服务
        addProfileService(classes); // ProfileTaskMutationService、ProfileTaskQueryService、ProfileTaskCache
        addOALService(classes); // OALEngineLoaderService
        addManagementService(classes); // UITemplateManagementService

        classes.add(CommandService.class); // 分析任务命令创建服务

        return classes.toArray(new Class[]{});
    }

    private void addManagementService(List<Class> classes) {
        classes.add(UITemplateManagementService.class); // UI模版管理服务
    }

    private void addProfileService(List<Class> classes) {
        classes.add(ProfileTaskMutationService.class); // 分析任务变更服务
        classes.add(ProfileTaskQueryService.class); // 分析任查询服务
        classes.add(ProfileTaskCache.class); // 待执行分析任务的缓存服务
    }

    private void addOALService(List<Class> classes) {
        classes.add(OALEngineLoaderService.class); // OAL引擎加载服务
    }

    /**
     * 这些服务支持从SkyWalking后端查询各种监控和分析数据。
     */
    private void addQueryService(List<Class> classes) {
        classes.add(TopologyQueryService.class);         // 拓扑查询服务
        classes.add(MetricsMetadataQueryService.class); // 指标元数据查询服务
        classes.add(MetricsQueryService.class);         // 指标数据查询服务
        classes.add(TraceQueryService.class);           // 链路查询服务
        classes.add(LogQueryService.class);             // 日志查询服务
        classes.add(MetadataQueryService.class);        // 元数据查询服务
        classes.add(AggregationQueryService.class);     // 指标聚合查询服务
        classes.add(AlarmQueryService.class);           // 告警查询服务
        classes.add(TopNRecordsQueryService.class);     // TopN记录查询服务
        classes.add(BrowserLogQueryService.class);      // 浏览器日志查询服务
        classes.add(EventQueryService.class);           // 事件查询服务
    }

    private void addServerInterface(List<Class> classes) {
        classes.add(GRPCHandlerRegister.class); // GRPCHandler注册服务
        classes.add(JettyHandlerRegister.class); // JettyHandler注册服务
    }

    /**
     * 这些服务主要用于框架内部操作和管理。
     */
    private void addInsideService(List<Class> classes) {
        classes.add(ModelCreator.class);          // 模块创建服务
        classes.add(IModelManager.class);         // 模块管理接口实现
        classes.add(ModelManipulator.class);      // 模块操作服务
        classes.add(RemoteClientManager.class);   // 远程OAP服务管理
        classes.add(RemoteSenderService.class);   // 远程OAP节点的指标发送服务
    }

    private void addCacheService(List<Class> classes) {
        classes.add(NetworkAddressAliasCache.class); // 服务/实例的临时网络地址的缓存
    }

    private void addReceiverInterface(List<Class> classes) {
        classes.add(SourceReceiver.class); // Source接收器
    }
}
