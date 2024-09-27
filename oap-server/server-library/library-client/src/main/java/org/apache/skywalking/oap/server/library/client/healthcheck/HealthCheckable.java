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

package org.apache.skywalking.oap.server.library.client.healthcheck;

import org.apache.skywalking.oap.server.library.util.HealthChecker;

/**
 * HealthCheckable indicate the client has the capacity of health check and need to register healthChecker.
 * <pre>
 * (HealthCheckable 表示客户端具有健康检查能力，需要注册 healthChecker。)
 * </pre>
 */
public interface HealthCheckable {

   /**
    * Register health checker.
    *
    * @param healthChecker HealthChecker to be registered.
    */
   void registerChecker(HealthChecker healthChecker);
}
