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

package org.apache.skywalking.oap.server.telemetry.api;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Open API to telemetry module, allow to create metrics instance with different type. Types inherits from prometheus
 * project, and plan to move to openmetrics APIs after it is ready.
 *
 * <pre>
 * (遥测模块 的 Open API，允许创建不同类型的 metrics 实例。
 * Types 继承自 prometheus 项目，并计划在准备就绪后迁移到 openmetrics APIs。)
 *
 * 提供创建指标的API。
 * </pre>
 */
public interface MetricsCreator extends Service {

    /** 健康指标前缀 */
    String HEALTH_METRIC_PREFIX = "health_check_";
    /**
     * Create a counter type metrics instance.
     * （创建 counter类型 的指标实例。）
     */
    CounterMetrics createCounter(String name, String tips, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues);

    /**
     * Create a gauge type metrics instance.
     * <pre>
     * (创建 gauge类型的 指标实例。)
     * </pre>
     */
    GaugeMetrics createGauge(String name, String tips, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues);

    /**
     * Create a Histogram type metrics instance.
     * <pre>
     * (创建 Histogram类型的 指标实例。)
     * </pre>
     *
     * @param buckets Time bucket for duration.（存放持续时间的桶）
     */
    HistogramMetrics createHistogramMetric(String name, String tips, MetricsTag.Keys tagKeys,
        MetricsTag.Values tagValues, double... buckets);

    /**
     * Create a Health Check gauge.
     * <pre>
     * (创建 HealthCheck 的 Gauge指标。)
     * </pre>
     */
    default HealthCheckMetrics createHealthCheckerGauge(String name, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues) {
        // 检查 name
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Require non-null or empty metric name");
        // new 一个 HealthCheckMetrics
        return new HealthCheckMetrics(
                createGauge(
                        Strings.lenientFormat("%s%s", HEALTH_METRIC_PREFIX, name),
                        Strings.lenientFormat("%s health check", name),
                        tagKeys, tagValues));
    }

    /**
     * Find out whether it's a health check metric.
     * @return true - 这是个 健康检查指标
     */
    default boolean isHealthCheckerMetrics(String name) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Require non-null or empty metric name");
        return name.startsWith(HEALTH_METRIC_PREFIX);
    }

    /**
     * Extract the raw module name
     * （提取原始模块名称）
     * @return 返回去掉健康指标前缀的metricName
     */
    default String extractModuleName(String metricName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(metricName), "Require non-null or empty metric name");
        return metricName.replace(HEALTH_METRIC_PREFIX, "");
    }
}
