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
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class CoreModuleConfig extends ModuleConfig {
    private String role = "Mixed";
    private String namespace;
    private String restHost;
    private int restPort;
    private String restContextPath;
    private int restMinThreads = 1;
    private int restMaxThreads = 200;
    /** RESTful 服务的连接器空闲超时（以毫秒为单位）。 */
    private long restIdleTimeOut = 30000;
    /** 提供给 RESTful 服务的接受器线程的线程优先级增量。 */
    private int restAcceptorPriorityDelta = 0;
    /** ServerSocketChannel Backlog of RESTful services. */
    private int restAcceptQueueSize = 0;

    private String gRPCHost;
    private int gRPCPort;
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath;
    private String gRPCSslCertChainPath;
    private String gRPCSslTrustedCAPath;
    /** 每个 传入连接 允许的最大并发调用数。默认为无限制。 */
    private int maxConcurrentCallsPerConnection;
    /** 设置允许在服务器上接收的最大Message大小。空表示 4 MiB。 */
    private int maxMessageSize;
    private int topNReportPeriod;
    /**
     * The period of L1 aggregation flush. Unit is ms.
     * L1 聚合刷新到 L2 聚合的时间段（以毫秒为单位）。
     */
    private long l1FlushPeriod = 500;
    /**
     * Enable database flush session.
     * 将指标数据缓存 1 分钟以减少数据库查询，以及 OAP 集群在该分钟内是否发生更改。
     */
    private boolean enableDatabaseSession;
    /**
     * The threshold of session time. Unit is ms. Default value is 70s.
     * （session时间的阈值（以毫秒为单位）。默认值为 70000。）
     */
    private long storageSessionTimeout = 70_000;
    /**
     * Activated level of down sampling aggregation.
     * <pre>
     * (已激活的下采样聚合级别。)
     * </pre>
     */
    private final List<String> downsampling;
    /**
     * The period of doing data persistence. Unit is second.
     * 持久计时器的执行周期（以秒为单位）。
     */
    @Setter
    private long persistentPeriod = 25;

    /** TTL 调度器的控制器。一旦禁用，TTL 将不起作用。 */
    private boolean enableDataKeeperExecutor = true;

    /** TTL 计划程序的执行周期（以分钟为单位）。执行并不意味着删除数据。存储提供程序（例如 ElasticSearch 存储）可以覆盖此属性。 */
    private int dataKeeperExecutePeriod = 5;
    /**
     * The time to live of all metrics data. Unit is day.
     *
     * 指标数据的生命周期（以天为单位），包括元数据。我们建议将 metricsDataTTL 设置为 >= recordDataTTL。最小值为 2。
     */
    private int metricsDataTTL = 3;
    /**
     * The time to live of all record data, including tracing. Unit is Day.
     * 记录数据的生命周期（以天为单位）。记录数据包括调用链、TOP N 样本记录和日志。最小值为 2。
     */
    private int recordDataTTL = 7;

    /** gRPC 服务器的池大小。 */
    private int gRPCThreadPoolSize;

    /** gRPC 服务器的队列大小。 */
    private int gRPCThreadPoolQueueSize;
    /**
     * Timeout for cluster internal communication, in seconds.
     * （集群内部通信的超时（以秒为单位）。）
     */
    private int remoteTimeout = 20;
    /**
     * network address alias 的最大大小。
     */
    private long maxSizeOfNetworkAddressAlias = 1_000_000L;

    /*
     * Following are cache setting for none stream(s)
     * （以下是无流的缓存设置）
     */

    /** 最多能容纳的分析任务 */
    private long maxSizeOfProfileTask = 10_000L;
    /**
     * Analyze profile snapshots paging size.
     * OAP查询中 快照分析的最大大小。
     */
    private int maxPageSizeOfQueryProfileSnapshot = 500;
    /**
     * Analyze profile snapshots max size.
     * OAP 分析的最大快照数。
     */
    private int maxSizeOfAnalyzeProfileSnapshot = 12000;
    /**
     * Extra model column are the column defined by {@link ScopeDefaultColumn.DefinedByField#requireDynamicActive()} ==
     * true. These columns of model are not required logically in aggregation or further query, and it will cause more
     * load for memory, network of OAP and storage.
     *
     * But, being activated, user could see the name in the storage entities, which make users easier to use 3rd party
     * tool, such as Kibana->ES, to query the data by themselves.
     */
    private boolean activeExtraModelColumns = false;
    /**
     * service name 的最大长度。
     */
    private int serviceNameMaxLength = 70;
    /**
     * service instance name 的最大长度。
     */
    private int instanceNameMaxLength = 70;
    /**
     * endpoint name 的最大长度
     * 注意：在目前的做法中，我们不建议长度超过 190。
     */
    private int endpointNameMaxLength = 150;
    /**
     * Define the set of span tag keys, which should be searchable through the GraphQL.
     * <pre>
     * (定义 span tag keys，这些 keys 应该可以通过 GraphQL 进行搜索。)
     *
     * 可搜索的 Traces Tag keys
     * </pre>
     *
     * @since 8.2.0
     */
    @Setter
    @Getter
    private String searchableTracesTags = DEFAULT_SEARCHABLE_TAG_KEYS;
    /**
     * Define the set of logs tag keys, which should be searchable through the GraphQL.
     * <pre>
     * (定义 logs tag keys，这些 keys 应该可以通过 GraphQL 进行搜索。)
     *
     * 可搜索的 Logs Tag keys
     * </pre>
     *
     * @since 8.4.0
     */
    @Setter
    @Getter
    private String searchableLogsTags = "";
    /**
     * Define the set of Alarm tag keys, which should be searchable through the GraphQL.
     * <pre>
     * (定义 Alarm tag keys，这些 keys 应可通过 GraphQL 进行搜索。)
     *
     * 可搜索的 Alarm Tag keys
     * </pre>
     *
     * @since 8.6.0
     */
    @Setter
    @Getter
    private String searchableAlarmTags = "";

    /**
     * The number of threads used to prepare metrics data to the storage.
     * （用于将指标数据准备到存储的线程数。）
     * @since 8.7.0
     */
    @Setter
    @Getter
    private int prepareThreads = 2;

    /** 按给定的 OpenAPI 定义 自动对 终端节点 进行分组。 */
    @Getter
    @Setter
    private boolean enableEndpointNameGroupingByOpenapi = true;

    /**
     * The maximum size in bytes allowed for request headers.
     * Use -1 to disable it.
     * （请求标头允许的最大字节大小。使用 -1 可禁用它。）
     */
    private int httpMaxRequestHeaderSize = 8192;

    public CoreModuleConfig() {
        this.downsampling = new ArrayList<>();
    }

    /**
     * OAP server could work in different roles.
     * <pre>
     * (OAP 服务器可以在不同的角色中工作。)
     * </pre>
     */
    public enum Role {
        /**
         * Default role. OAP works as the {@link #Receiver} and {@link #Aggregator}
         * （默认角色。OAP充当 接收器 和 聚合器）
         */
        Mixed,
        /**
         * Receiver mode OAP open the service to the agents, analysis and aggregate the results and forward the results
         * to {@link #Mixed} and {@link #Aggregator} roles OAP. The only exception is for {@link
         * org.apache.skywalking.oap.server.core.analysis.record.Record}, they don't require 2nd round distributed
         * aggregation, is being pushed into the storage from the receiver OAP directly.
         * <pre>
         * (接收器模式OAP 向代理打开服务，分析和聚合结果，并将结果转发给 Mixed 和 Aggregator 角色 OAP。
         * 唯一的例外是 core.analysis.record.Record，它们不需要第二轮分布式聚合，直接从接收器 OAP 推送到 storage 中。)
         * </pre>
         */
        Receiver,
        /**
         * Aggregator mode OAP receives data from {@link #Mixed} and {@link #Aggregator} OAP nodes, and do 2nd round
         * aggregation. Then save the final result to the storage.
         * <pre>
         * (聚合器模式OAP 从 混合和聚合器OAP节点接收数据，并执行第 2 轮聚合。然后将最终结果保存到 storage 中。)
         * </pre>
         */
        Aggregator;

        public static Role fromName(String name) {
            for (Role role : Role.values()) {
                if (role.name().equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return Mixed;
        }
    }

    /**
     * SkyWalking Java Agent provides the recommended tag keys for other language agents or SDKs. This field declare the
     * recommended keys should be searchable.
     * <pre>
     * (SkyWalking Java Agent 为其他语言 Agent 或 SDK 提供推荐的标签键。此字段声明建议的 keys 应可搜索。)
     * </pre>
     */
    private static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
        Const.COMMA,
        "http.method",
        "status_code",
        "db.type",
        "db.instance",
        "mq.queue",
        "mq.topic",
        "mq.broker"
    );
}
