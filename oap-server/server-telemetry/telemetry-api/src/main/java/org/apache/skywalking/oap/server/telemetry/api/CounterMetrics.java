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

/**
 * A counter is a cumulative metrics that represents a single monotonically increasing counter whose value can only
 * increase or be reset to zero on restart. For example, you can use a counter to represent the number of requests
 * served, tasks completed, or errors.z
 *
 * <pre>
 * (计数器是一个“累积指标”，表示单个单调递增的计数器，其值只能在重新启动时增加或重置为零。
 * 例如，您可以使用计数器来表示已服务的请求数、已完成的任务数或错误数。z)
 * </pre>
 */
public interface CounterMetrics {
    /**
     * Increase 1 to counter
     */
    void inc();

    /**
     * Increase the given value to the counter
     */
    void inc(double value);
}
