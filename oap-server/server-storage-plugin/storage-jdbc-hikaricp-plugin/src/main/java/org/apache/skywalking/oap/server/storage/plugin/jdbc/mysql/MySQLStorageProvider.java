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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
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
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2UITemplateManagementDAO;

/**
 * MySQL storage provider should be secondary choice for production usage as SkyWalking storage solution. It enhanced
 * and came from H2StorageProvider, but consider more in using in production.
 *
 * Because this module is not really related to MySQL, instead, it is based on MySQL SQL style with JDBC, so, by having
 * this storage implementation, we could also use this in MySQL-compatible projects, such as, Apache ShardingSphere,
 * TiDB
 *
 * <pre>
 * (MySQL存储提供商 应该是 SkyWalking 存储解决方案的第二选择。
 * 它增强并来自 H2StorageProvider，但在生产中使用时要考虑更多。
 * 因为这个模块与MySQL无关，相反，它是基于 MySQL SQL 风格的 JDBC，
 * 所以，通过这个存储实现，我们也可以在MySQL兼容的项目中使用它，比如 Apache ShardingSphere, TiDB)
 * </pre>
 */
@Slf4j
public class MySQLStorageProvider extends ModuleProvider {

    private MySQLStorageConfig config;
    private JDBCHikariCPClient mysqlClient;

    public MySQLStorageProvider() {
        config = new MySQLStorageConfig();
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    /**
     * prepare 阶段，用于初始化和注册所有依赖H2数据库客户端的服务实现。
     * 此方法会根据配置创建数据库连接，并将相应的DAO（数据访问对象）实现注册到系统服务中。
     */
    @Override
    public void prepare() throws ServiceNotProvidedException {


        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        // 使用 配置属性 创建 MySQL HikariCP 客户端，用于数据库连接池管理
        mysqlClient = new JDBCHikariCPClient(config.getProperties());

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(mysqlClient, config.getMaxSizeOfBatchSql(), config.getAsyncBatchPersistentPoolSize()));
        this.registerServiceImplementation(
            StorageDAO.class,
            new H2StorageDAO(
                getManager(), mysqlClient, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag())
        );
        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new H2NetworkAddressAliasDAO(mysqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new H2MetricsQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            ITraceQueryDAO.class,
            new MySQLTraceQueryDAO(
                getManager(),
                mysqlClient,
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
            )
        );
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new MysqlBrowserLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new H2MetadataQueryDAO(mysqlClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new MySQLAggregationQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new MySQLAlarmQueryDAO(
                mysqlClient,
                getManager(),
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(mysqlClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            ILogQueryDAO.class,
            new MySQLLogQueryDAO(
                mysqlClient,
                getManager(),
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
            )
        );

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(mysqlClient));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(mysqlClient));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new H2UITemplateManagementDAO(mysqlClient));

        this.registerServiceImplementation(IEventQueryDAO.class, new H2EventQueryDAO(mysqlClient));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

        // 获取 ConfigService 的实现，用于读取 配置信息。
        final ConfigService configService = getManager().find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(ConfigService.class);

        // 验证 可搜索的Trace标签数量 是否超过数据库数组列的最大限制
        final int numOfSearchableTags = configService.getSearchableTracesTags().split(Const.COMMA).length;
        if (numOfSearchableTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableTracesTags[" + numOfSearchableTags
                                               + "] * numOfSearchableValuesPerTag[" + config.getNumOfSearchableValuesPerTag()
                                               + "] > maxSizeOfArrayColumn[" + config.getMaxSizeOfArrayColumn()
                                               + "]. Potential out of bound in the runtime.");
        }

        // 验证 可搜索的日志标签数量 是否超过数据库数组列的最大限制
        final int numOfSearchableLogsTags = configService.getSearchableLogsTags().split(Const.COMMA).length;
        if (numOfSearchableLogsTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableLogsTags[" + numOfSearchableLogsTags
                                               + "] * numOfSearchableValuesPerTag[" + config.getNumOfSearchableValuesPerTag()
                                               + "] > maxSizeOfArrayColumn[" + config.getMaxSizeOfArrayColumn()
                                               + "]. Potential out of bound in the runtime.");
        }

        // 验证 可搜索的告警标签数量 是否超过数据库数组列的最大限制
        final int numOfSearchableAlarmTags = configService.getSearchableAlarmTags().split(Const.COMMA).length;
        if (numOfSearchableAlarmTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableAlarmTags[" + numOfSearchableAlarmTags
                    + "] * numOfSearchableValuesPerTag[" + config.getNumOfSearchableValuesPerTag()
                    + "] > maxSizeOfArrayColumn[" + config.getMaxSizeOfArrayColumn()
                    + "]. Potential out of bound in the runtime.");
        }

        try {
            // 尝试连接数据库
            mysqlClient.connect();

            // 创建 MySQLTableInstaller 实例，用于安装或更新数据库表结构
            MySQLTableInstaller installer = new MySQLTableInstaller(
                mysqlClient, getManager(), config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag()
            );

            // 注册 ModelListener 以便在模型变化时自动调整数据库表结构
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
