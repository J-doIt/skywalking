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

package org.apache.skywalking.oap.server.core.storage.ttl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * TTL = Time To Live
 *
 * DataTTLKeeperTimer is an internal timer, it drives the {@link IHistoryDeleteDAO} to remove the expired data. TTL
 * configurations are provided in {@link CoreModuleConfig}, some storage implementations, such as ES6/ES7, provides an
 * override TTL, which could be more suitable for the implementation. No matter which TTL configurations are set, they
 * are all driven by this timer.
 */
@Slf4j
public enum DataTTLKeeperTimer {
    INSTANCE;

    private ModuleManager moduleManager;
    private ClusterNodesQuery clusterNodesQuery;
    private CoreModuleConfig moduleConfig;

    public void start(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.clusterNodesQuery = moduleManager.find(ClusterModule.NAME).provider().getService(ClusterNodesQuery.class);
        this.moduleConfig = moduleConfig;

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleAtFixedRate(
                     new RunnableWithExceptionProtection(
                         this::delete,
                         t -> log.error("Remove data in background failure.", t)
                     ), moduleConfig
                         .getDataKeeperExecutePeriod(), moduleConfig.getDataKeeperExecutePeriod(), TimeUnit.MINUTES);
    }

    /**
     * DataTTLKeeperTimer starts in every OAP node, but the deletion only work when it is as the first node in the OAP
     * node list from {@link ClusterNodesQuery}.
     *
     * <pre>
     * (DataTTLKeeperTimer 在每个 OAP 节点中启动，但仅当它是 OAP 节点列表中 ClusterNodesQuery的第一个节点时，删除才有效。)
     *
     * 用于删除过期的指标数据并检查数据边界。
     * 此操作首先获取所有模型定义，然后选择是否跳过清理阶段（如果第一个非自身节点存在），
     * 接着从存储中移除过期指标，最后检查所有模型的数据边界。
     * </pre>
     */
    private void delete() {

        // 从Core模块获取模型管理器服务
        IModelManager modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelManager.class);

        // 获取所有已注册的模型
        List<Model> models = modelGetter.allModels();

        try {
            // 查询集群中所有远程实例
            List<RemoteInstance> remoteInstances = clusterNodesQuery.queryRemoteNodes();

            // 如果远程实例列表不为空且第一个实例不是当前节点自己，则日志记录并跳过删除阶段，因为假设第一个节点负责此任务
            if (CollectionUtils.isNotEmpty(remoteInstances) && !remoteInstances.get(0).getAddress().isSelf()) {
                log.info(
                    "The selected first getAddress is {}. The remove stage is skipped.",
                    remoteInstances.get(0).toString()
                );
                return;
            }

            log.info("Beginning to remove expired metrics from the storage.");
            // 遍历所有模型，并对每个模型执行清理操作
            models.forEach(this::execute);
        } finally {
            log.info("Beginning to inspect data boundaries.");
            // 检查所有模型的数据范围或边界条件
            this.inspect(models);
        }
    }
    /**
     * 根据模型配置，尝试从存储中删除指定模型的历史数据。
     * 只有时间序列模型会被处理，并且会根据模型是否记录数据选择不同的 TTL（生存时间）进行数据删除。
     *
     * @param model 要处理的模型实例
     */
    private void execute(Model model) {
        try {
            // 如果模型非时间序列类型，则直接返回
            if (!model.isTimeSeries()) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(
                    "Is record? {}. RecordDataTTL {}, MetricsDataTTL {}",
                    model.isRecord(),
                    moduleConfig.getRecordDataTTL(),
                    moduleConfig.getMetricsDataTTL());
            }
            // 使用 storage 模块 提供的 IHistoryDeleteDAO 服务 删除指定模型的历史数据
            // 使用不同的TTL（记录数据或指标数据）
            moduleManager.find(StorageModule.NAME)
                         .provider()
                         .getService(IHistoryDeleteDAO.class)
                         // 删除指定模型的历史数据
                         .deleteHistory(model, Metrics.TIME_BUCKET,
                                        model.isRecord() ? moduleConfig.getRecordDataTTL() : moduleConfig.getMetricsDataTTL()
                         );
        } catch (IOException e) {
            log.warn("History of {} delete failure", model.getName());
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 检查所有模型的数据边界，确保数据的连续性和完整性。
     * 这通常涉及到验证数据存储中是否存在不应有的数据空白或超出预期范围的情况。
     *
     * @param models 所有需要检查的模型列表
     */
    private void inspect(List<Model> models) {
        try {
            // 从 storage 模块 获取 IHistoryDeleteDAO 服务 并执行模型数据边界的检查
            moduleManager.find(StorageModule.NAME)
                         .provider()
                         .getService(IHistoryDeleteDAO.class)
                         .inspect(models, Metrics.TIME_BUCKET); // 模型数据边界的检查
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
