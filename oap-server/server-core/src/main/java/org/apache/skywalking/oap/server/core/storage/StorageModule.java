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

package org.apache.skywalking.oap.server.core.storage;

import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * StorageModule provides the capabilities(services) to interact with the database. With different databases, this
 * module could have different providers, such as currently, H2, MySQL, ES, TiDB.
 *
 * <pre>
 * (StorageModule 提供了与数据库交互的能力（服务）。
 * 对于不同的数据库，该模块可能具有不同的提供者，例如 currently、H2、MySQL、ES、TiDB。)
 * </pre>
 */
public class StorageModule extends ModuleDefine {

    public static final String NAME = "storage";

    public StorageModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            StorageBuilderFactory.class, // 存储构建器工厂接口，用于创建存储构建器实例
            IBatchDAO.class, // 批量数据访问对象接口，用于执行批量数据库操作
            StorageDAO.class, // 基础存储数据访问对象接口，提供通用数据存取方法
            IHistoryDeleteDAO.class, // 历史数据删除数据访问对象接口，负责清理过期数据
            INetworkAddressAliasDAO.class, // 网络地址别名数据访问对象接口，管理网络地址与别名映射
            ITopologyQueryDAO.class, // 拓扑查询数据访问对象接口，用于获取服务拓扑信息
            IMetricsQueryDAO.class, // 指标查询数据访问对象接口，用于检索各类性能指标数据
            ITraceQueryDAO.class, // 跟踪查询数据访问对象接口，用于查询分布式跟踪信息
            IMetadataQueryDAO.class, // 元数据查询数据访问对象接口，提供元数据查询服务
            IAggregationQueryDAO.class, // 聚合查询数据访问对象接口，用于执行数据聚合查询
            IAlarmQueryDAO.class, // 告警查询数据访问对象接口，用于检索告警记录
            ITopNRecordsQueryDAO.class, // Top N 记录查询数据访问对象接口，用于获取排名数据
            ILogQueryDAO.class, // 日志查询数据访问对象接口，用于检索日志数据
            IProfileTaskQueryDAO.class, // 性能剖析任务查询数据访问对象接口，管理性能剖析任务信息
            IProfileTaskLogQueryDAO.class, // 性能剖析任务日志查询数据访问对象接口，查询性能剖析任务产生的日志
            IProfileThreadSnapshotQueryDAO.class, // 性能剖析线程快照查询数据访问对象接口，获取线程快照详情
            UITemplateManagementDAO.class, // UI模板管理数据访问对象接口，用于管理用户界面模板
            IBrowserLogQueryDAO.class, // 浏览器日志查询数据访问对象接口，检索浏览器端日志
            IEventQueryDAO.class // 事件查询数据访问对象接口，查询事件记录
        };
    }
}
