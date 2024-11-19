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

package org.apache.skywalking.oap.server.core.config;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Load settings from component-libraries.yml this file includes all component defines, and the component mappings,
 * which declare the real server type based on client component.
 * <pre>
 * (从 component-libraries.yml 加载 settings 。
 * 这个文件包括所有 “组件定义” 和 “组件映射”，它们根据客户端组件声明后端服务器类型。)
 * 【组件库登记服务】
 * </pre>
 */
public class ComponentLibraryCatalogService implements IComponentLibraryCatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentLibraryCatalogService.class);
    private static final String COMPONENT_SERVER_MAPPING_SECTION = "Component-Server-Mappings";

    /** ≤ 组件名 , 组件Id ≥ */
    private Map<String, Integer> componentName2Id;
    /** ≤ 组件Id , 组件名 ≥ */
    private Map<Integer, String> componentId2Name;
    /** ≤ 组件Id , 服务Id ≥ */
    private Map<Integer, Integer> componentId2ServerId;

    public ComponentLibraryCatalogService() throws InitialComponentCatalogException {
        init();
    }

    @Override
    public int getComponentId(String componentName) {
        return componentName2Id.get(componentName);
    }

    @Override
    public int getServerIdBasedOnComponent(int componentId) {
        Integer serverComponentId = componentId2ServerId.get(componentId);
        return serverComponentId == null ? componentId : serverComponentId;
    }

    @Override
    public String getComponentName(int componentId) {
        String componentName = componentId2Name.get(componentId);

        return componentName == null ? componentId2Name.get(0) : componentName;
    }

    @Override
    public String getServerNameBasedOnComponent(int componentId) {
        Integer serverComponentId = componentId2ServerId.get(componentId);
        return serverComponentId == null ? getComponentName(componentId) : getComponentName(serverComponentId);
    }

    private void init() throws InitialComponentCatalogException {
        componentName2Id = new HashMap<>();
        componentName2Id.put("N/A", 0);
        componentId2Name = new HashMap<>();
        componentId2Name.put(0, "N/A");
        componentId2ServerId = new HashMap<>();

        Map<String, String> nameMapping = new HashMap<>();
        try {
            // 加载 component-libraries.yml 文件
            Reader applicationReader = ResourceUtils.read("component-libraries.yml");
            Yaml yaml = new Yaml();
            // 将 yaml中的数据 转为 Map 格式
            Map map = yaml.loadAs(applicationReader, Map.class);

            map.forEach((componentName/* 组件/服务/Component-Server-Mappings */, settingCollection) -> {
                Map settings = (Map) settingCollection;
                // 如果 componentName 是  "Component-Server-Mappings"，则是 list 结构
                    // eg：
                    // Component-Server-Mappings
                    //   mysql-connector-java: Mysql
                    //   MySqlConnector: Mysql
                if (COMPONENT_SERVER_MAPPING_SECTION.equals(componentName)) {
                    settings.forEach((name/* 组件名 */, serverName/* 服务名 */) -> {
                        nameMapping.put((String) name, (String) serverName);
                    });
                } else {
                    // 如果 componentName 不是  "Component-Server-Mappings"，则是 map 结构
                    // eg：
                    // mysql-connector-java:
                    //  id: 33
                    //  languages: Java
                    // MySqlConnector:
                    //  id: 3008
                    //  languages: C#
                    Integer componentId = (Integer) settings.get("id");
                    componentName2Id.put((String) componentName, componentId);
                    componentId2Name.put(componentId, (String) componentName);
                }
            });

            nameMapping.forEach((name/* 组件名 */, serverName/* 服务名 */) -> {
                // 检查在 Component-Server-Mappings 中存在的 “组件名”，但是不在 component define 中
                if (!componentName2Id.containsKey(name)) {
                    throw new InitialComponentCatalogException(
                        "Component name [" + name + "] in Component-Server-Mappings doesn't exist in component define. ");
                }
                // 检查在 Component-Server-Mappings 中存在的 “服务名”，但是不在 component define 中
                if (!componentName2Id.containsKey(serverName)) {
                    throw new InitialComponentCatalogException(
                        "Server componentId name [" + serverName + "] in Component-Server-Mappings doesn't exist in component define. ");
                }

                componentId2ServerId.put(componentName2Id.get(name)/* 组件Id */, componentName2Id.get(serverName)/* 服务Id */);
            });
            nameMapping.clear();
        } catch (FileNotFoundException e) {
            LOGGER.error("component-libraries.yml not found.", e);
        }

    }
}
