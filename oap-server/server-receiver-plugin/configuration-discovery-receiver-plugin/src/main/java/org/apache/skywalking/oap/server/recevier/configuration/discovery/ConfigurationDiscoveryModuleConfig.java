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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class ConfigurationDiscoveryModuleConfig extends ModuleConfig {
    /**
     *  If true, agent receives the latest configuration every time even without change. 
     *  In default, OAP uses SHA512 message digest mechanism to detect changes of configuration.
     *  <pre>
     * (如果为 true，则 agent 每次都会收到最新的配置，即使没有更改。
     * 默认情况下，OAP 使用 SHA512消息摘要机制 来检测配置更改。)
     * </pre>
     */
    @Setter
    @Getter
    private boolean disableMessageDigest = false;
}
