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
 * The telemetry context which the metrics instances may need to know.
 * <pre>
 * (指标实例可能需要了解的遥测上下文。)
 * </pre>
 */
public enum TelemetryRelatedContext {
    INSTANCE;

    private volatile String id = null;

    TelemetryRelatedContext() {
    }

    /**
     * Set a global ID to represent the current oap instance
     * <pre>
     * (设置 全局ID 以表示当前 oap 实例)
     * </pre>
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the oap instance ID, if be set before.
     * <pre>
     * (获取 oap 实例 ID（如果之前已设置）。)
     * </pre>
     *
     * @return id or null.
     */
    public String getId() {
        return id;
    }
}
