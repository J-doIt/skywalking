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

import com.google.common.hash.Hashing;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * AgentConfigurationsWatcher used to handle dynamic configuration changes.
 * <pre>
 * (AgentConfigurationsWatcher 用于处理动态配置更改。)
 *
 * 配置变更观察者：itemName 是 agentConfigurations
 * </pre>
 */
public class AgentConfigurationsWatcher extends ConfigChangeWatcher {
    /** 该watcher关注的配置项（itemName：agentConfigurations）对应的值 */
    private volatile String settingsString;
    /** Agent动态配置表 */
    private volatile AgentConfigurationsTable agentConfigurationsTable;
    /** 空的Agent动态配置 */
    private final AgentConfigurations emptyAgentConfigurations;

    public AgentConfigurationsWatcher(ModuleProvider provider) {
        super(ConfigurationDiscoveryModule.NAME, provider, "agentConfigurations");
        this.settingsString = null;
        // 初始化 Agent动态配置表
        this.agentConfigurationsTable = new AgentConfigurationsTable();
        // noinspection UnstableApiUsage
        // 初始化 空的Agent动态配置
        this.emptyAgentConfigurations = new AgentConfigurations(
            null, new HashMap<>(),
            Hashing.sha512().hashString("EMPTY", StandardCharsets.UTF_8).toString()
        );
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        // 如果 配置变更事件 是 DELETE，重置值
        if (value.getEventType().equals(EventType.DELETE)) {
            settingsString = null;
            this.agentConfigurationsTable = new AgentConfigurationsTable();
        } else {
            // 如果 配置变更事件 是 UPDATE、INSERT

            // 更新为最新值
            settingsString = value.getNewValue();
            // 初始化 AgentConfigurations读取器
            AgentConfigurationsReader agentConfigurationsReader =
                new AgentConfigurationsReader(new StringReader(value.getNewValue()));
            // 将 newValue 转为 AgentConfigurationsTable
            this.agentConfigurationsTable = agentConfigurationsReader.readAgentConfigurationsTable();
        }
    }

    @Override
    public String value() {
        return settingsString;
    }

    /**
     * Get service dynamic configuration information, if there is no dynamic configuration information, return to empty
     * dynamic configuration to prevent the server from deleted the dynamic configuration, but it does not take effect
     * on the agent side.
     *
     * <pre>
     * (获取服务动态配置信息，如果没有动态配置信息，则返回为空的动态配置，以防止 server 删除动态配置，但在 agent 端不生效。)
     * </pre>
     *
     * @param service Service name to be queried（需要查询的服务名称）
     * @return Service dynamic configuration information（服务的动态配置信息）
     */
    public AgentConfigurations getAgentConfigurations(String service) {
        AgentConfigurations agentConfigurations = agentConfigurationsTable.getAgentConfigurationsCache().get(service);
        if (null == agentConfigurations) {
            return emptyAgentConfigurations;
        } else {
            return agentConfigurations;
        }
    }
}
