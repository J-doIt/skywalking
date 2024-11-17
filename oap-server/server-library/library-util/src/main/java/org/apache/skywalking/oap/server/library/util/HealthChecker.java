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

package org.apache.skywalking.oap.server.library.util;

/**
 * Health checker provides methods to register the health status.
 * <pre>
 * (Health Checker 提供注册运行状况的方法。)
 * </pre>
 */
public interface HealthChecker {

    /**
     * It's health.
     */
    void health();

    /**
     * It's unHealth.
     *
     * @param t details of unhealthy status
     */
    void unHealth(Throwable t);

    /**
     * It's unHealth.
     *
     * @param reason details reason of unhealthy status
     */
    void unHealth(String reason);
}
