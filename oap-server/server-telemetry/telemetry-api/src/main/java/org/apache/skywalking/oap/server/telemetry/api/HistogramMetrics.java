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

import java.io.Closeable;

/**
 * A histogram samples observations (usually things like request durations or response sizes) and counts them in
 * configurable buckets. It also provides a sum of all observed values.
 * <pre>
 * (histogram 对观察结果（通常是请求持续时间或响应大小等内容）进行采样，并在可配置的存储桶中对其进行计数。它还提供所有观测值的总和。)
 * </pre>
 */
public abstract class HistogramMetrics {

    /**
     * 创建一个 Timer。
     */
    public Timer createTimer() {
        return new Timer(this);
    }

    /**
     * Observe an execution, get a duration in second.
     * <pre>
     * (执行观察，获取以秒为单位的持续时间。)
     * </pre>
     *
     * @param value duration in second.
     */
    public abstract void observe(double value);

    public class Timer implements Closeable {
        private final HistogramMetrics metrics;
        /** 开始时间（纳秒） */
        private final long startNanos;
        /** 持续时间（秒） */
        private double duration;

        public Timer(HistogramMetrics metrics) {
            this.metrics = metrics;
            startNanos = System.nanoTime();
        }

        public void finish() {
            long endNanos = System.nanoTime();
            // 1.0E9D：科学计数法表示的双精度浮点数
            //      1.0E9 表示 1.0×(10^9) ，即 1,000,000,000。
            //      D 是双精度浮点数（double）的后缀。
            // 持续时间 = (结束时间 - 开始时间) / 1_000_000_000 秒（纳秒换算为秒）
            duration = (double) (endNanos - startNanos) / 1.0E9D;
            // 执行观察
            metrics.observe(duration);
        }

        @Override
        public void close() {
            finish();
        }
    }
}
