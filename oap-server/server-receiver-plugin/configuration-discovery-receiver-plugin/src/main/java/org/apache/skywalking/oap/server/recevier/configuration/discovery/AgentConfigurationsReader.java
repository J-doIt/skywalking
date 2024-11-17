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
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Used to parse the String configuration to AgentConfigurations.
 * <pre>
 * (用于将 String 配置解析为 AgentConfigurations。)
 * </pre>
 */
@Slf4j
public class AgentConfigurationsReader {
    /** yaml 配置数据的 map 形式 */
    private Map yamlData;

    public AgentConfigurationsReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(inputStream);
    }

    /**
     * @param io yaml 中的 configurations 的内容
     */
    public AgentConfigurationsReader(Reader io) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(io);
    }

    /**
     * 将 String（yml下的configurations） 配置解析为 AgentConfigurationsTable。
     * @return
     */
    public AgentConfigurationsTable readAgentConfigurationsTable() {
        AgentConfigurationsTable agentConfigurationsTable = new AgentConfigurationsTable();
        try {
            if (Objects.nonNull(yamlData)) {
                Map configurationsData = (Map) yamlData.get("configurations");
                if (configurationsData != null) {
                    configurationsData.forEach((k/* AgentConfigurations.service */, v/* map */) -> {
                        Map map = (Map) v;
                        StringBuilder serviceConfigStr = new StringBuilder();
                        Map<String, String> config = new HashMap<>(map.size());
                        map.forEach((key, value) -> {
                            // 将 map 的 key-value 放入 config
                            config.put(key.toString(), value.toString());
                            // 将 key:value 拼接到 serviceConfigStr
                            serviceConfigStr.append(key).append(":").append(value);
                        });

                        // noinspection UnstableApiUsage
                        // new 一个 AgentConfigurations
                        AgentConfigurations agentConfigurations = new AgentConfigurations(
                            k.toString(), config,
                            Hashing.sha512().hashString(/* 通过 Hashing.sha512 为 “key:value” 生成 uuid */
                                serviceConfigStr.toString(), StandardCharsets.UTF_8).toString()
                        );
                        // put 到 agentConfigurationsTable
                        agentConfigurationsTable.getAgentConfigurationsCache()
                                                .put(agentConfigurations.getService(), agentConfigurations);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Read ConfigurationDiscovery configurations error.", e);
        }
        return agentConfigurationsTable;
    }
}
