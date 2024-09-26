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

package org.apache.skywalking.oap.server.starter.config;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ProviderNotFoundException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Initialize collector settings with following sources. Use application.yml as primary setting, and fix missing setting
 * by default settings in application-default.yml.
 * <p>
 * At last, override setting by system.properties and system.envs if the key matches moduleName.provideName.settingKey.
 *
 * <pre>
 * ()
 *
 * 配置加载器
 * </pre>
 */
@Slf4j
public class ApplicationConfigLoader implements ConfigLoader<ApplicationConfiguration> {
    private static final String DISABLE_SELECTOR = "-";
    private static final String SELECTOR = "selector";

    private final Yaml yaml = new Yaml();

    @Override
    public ApplicationConfiguration load() throws ConfigFileNotFoundException {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        // 加载 配置
        this.loadConfig(configuration);
        // 通过 环境变量 重写 配置
        this.overrideConfigBySystemEnv(configuration);
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws ConfigFileNotFoundException {
        try {
            // 从 YAML 配置，读取模块配置映射
            Reader applicationReader = ResourceUtils.read("application.yml");
            // 将YAML内容转换为Map结构
            Map<String, Map<String, Object>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
            if (CollectionUtils.isNotEmpty(moduleConfig)) {
                // 根据 selector 筛选配置
                selectConfig(moduleConfig);
                // 遍历筛选后的模块配置
                moduleConfig.forEach((moduleName, providerConfig) -> {
                    // 若当前模块有配置的提供者
                    if (providerConfig.size() > 0) {
                        log.info("Get a module define from application.yml, module name: {}", moduleName);
                        // 添加模块到应用配置中
                        ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(
                            moduleName);
                        // 遍历 提供者配 ，置并处理其属性
                        providerConfig.forEach((providerName, config) -> {
                            log.info(
                                "Get a provider define belong to {} module, provider name: {}", moduleName,
                                providerName
                            );
                            final Map<String, ?> propertiesConfig = (Map<String, ?>) config;
                            final Properties properties = new Properties();
                            if (propertiesConfig != null) {
                                propertiesConfig.forEach((propertyName, propertyValue) -> {
                                    // 处理嵌套的属性值
                                    if (propertyValue instanceof Map) {
                                        // 将嵌套的属性添加到子Properties中
                                        Properties subProperties = new Properties();
                                        ((Map) propertyValue).forEach((key, value) -> {
                                            subProperties.put(key, value);
                                            // 替换 property 和 log
                                            replacePropertyAndLog(key, value, subProperties, providerName);
                                        });
                                        properties.put(propertyName, subProperties);
                                    } else {
                                        properties.put(propertyName, propertyValue);
                                        replacePropertyAndLog(propertyName, propertyValue, properties, providerName);
                                    }
                                });
                            }
                            // // 将处理后的 Properties 添加到模块的提供者配置中
                            moduleConfiguration.addProviderConfiguration(providerName, properties);
                        });
                    } else {
                        log.warn(
                            "Get a module define from application.yml, but no provider define, use default, module name: {}",
                            moduleName
                        );
                    }
                });
            }
        } catch (FileNotFoundException e) {
            throw new ConfigFileNotFoundException(e.getMessage(), e);
        }
    }

    private void replacePropertyAndLog(final Object propertyName, final Object propertyValue, final Properties target,
                                       final Object providerName) {
        final String valueString = PropertyPlaceholderHelper.INSTANCE
            .replacePlaceholders(propertyValue + "", target);
        if (valueString != null) {
            if (valueString.trim().length() == 0) {
                target.replace(propertyName, valueString);
                log.info("Provider={} config={} has been set as an empty string", providerName, propertyName);
            } else {
                // Use YAML to do data type conversion.
                final Object replaceValue = yaml.load(valueString);
                if (replaceValue != null) {
                    target.replace(propertyName, replaceValue);
                    log.info(
                        "Provider={} config={} has been set as {}",
                        providerName,
                        propertyName,
                        replaceValue.toString()
                    );
                }
            }
        }
    }

    private void overrideConfigBySystemEnv(ApplicationConfiguration configuration) {
        for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
            // 重写 模块 的配置
            overrideModuleSettings(configuration, prop.getKey().toString(), prop.getValue().toString());
        }
    }

    /**
     * selector 为新增的配置项，它可能通过名称匹配，执行需要的模块实现
     */
    private void selectConfig(final Map<String, Map<String, Object>> moduleConfiguration) {
        // 获取模块配置的迭代器
        Iterator<Map.Entry<String, Map<String, Object>>> moduleIterator = moduleConfiguration.entrySet().iterator();
        // 循环 组件配置
        while (moduleIterator.hasNext()) {
            Map.Entry<String, Map<String, Object>> entry = moduleIterator.next();
            // 当前模块名称
            final String moduleName = entry.getKey();
            // 当前模块的提供者配置
            final Map<String, Object> providerConfig = entry.getValue();
            // 如果配置中没有selector键，则跳过当前循环继续下一个模块
            if (!providerConfig.containsKey(SELECTOR)) {
                continue;
            }
            // 获取selector的值
            final String selector = (String) providerConfig.get(SELECTOR);
            // 使用系统属性替换配置中selector的${name}形式的占位符
            final String resolvedSelector = PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(
                selector, System.getProperties()
            );
            // 移除配置中key与解析后的selector不匹配的所有项
            providerConfig.entrySet().removeIf(e -> !resolvedSelector.equals(e.getKey()));

            // 如果移除后配置为空，说明没有匹配的提供者，但selector不是特殊值"-"
            if (!providerConfig.isEmpty()) {
                continue;
            }

            // 检查是否明确禁用了selector，如果没有并且配置为空则抛出异常
            if (!DISABLE_SELECTOR.equals(resolvedSelector)) {
                throw new ProviderNotFoundException(
                    "no provider found for module " + moduleName + ", " +
                        "if you're sure it's not required module and want to remove it, " +
                        "set the selector to -"
                );
            }

            // now the module can be safely removed
            // 如果selector是特殊值"-"，表示允许移除无提供者的模块，
            // 安全地从模块配置中移除当前模块
            moduleIterator.remove();
            log.info("Remove module {} without any provider", moduleName);
        }
    }

    /**
     *
     * <pre>
     * 根据指定的键值对覆盖应用配置中的模块设置。
     * 键（key）格式应为 "moduleName.providerName.settingKey"，以点(".")分隔。
     *    比如，要将 core.default.restHost 这个参数覆写为127.0.0.1，在启动参数上增加配置-Dcore.default.restHost=127.0.0.1即可。
     * </pre>
     *
     * @param configuration 应用的全局配置实例，包含所有模块的配置信息
     * @param key 用于定位模块、提供者及具体设置的复合键
     * @param value 要设置的新值，将替换原有配置中的对应值
     */
    private void overrideModuleSettings(ApplicationConfiguration configuration, String key, String value) {
        int moduleAndConfigSeparator = key.indexOf('.');
        if (moduleAndConfigSeparator <= 0) {
            return;
        }
        // 模块名
        String moduleName = key.substring(0, moduleAndConfigSeparator);
        // 提供者 设置的子键
        String providerSettingSubKey = key.substring(moduleAndConfigSeparator + 1);
        // 获取指定模块的配置
        ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.getModuleConfiguration(
            moduleName);
        if (moduleConfiguration == null) {
            return; // 如果模块配置不存在，直接返回
        }

        int providerAndConfigSeparator = providerSettingSubKey.indexOf('.');
        if (providerAndConfigSeparator <= 0) {
            return;
        }
        // 提供者名和设置键
        String providerName = providerSettingSubKey.substring(0, providerAndConfigSeparator);
        String settingKey = providerSettingSubKey.substring(providerAndConfigSeparator + 1);
        // 检查模块配置中是否存在指定的提供者
        if (!moduleConfiguration.has(providerName)) {
            return;
        }
        // 获取提供者的配置属性
        Properties providerSettings = moduleConfiguration.getProviderConfiguration(providerName);
        if (!providerSettings.containsKey(settingKey)) {
            return;
        }
        // 获取原始值并确定其类型，以便安全地进行类型转换
        Object originValue = providerSettings.get(settingKey);
        Class<?> type = originValue.getClass();
        // 根据原始值的类型设置新的值
        if (type.equals(int.class) || type.equals(Integer.class))
            providerSettings.put(settingKey, Integer.valueOf(value));
        else if (type.equals(String.class))
            providerSettings.put(settingKey, value);
        else if (type.equals(long.class) || type.equals(Long.class))
            providerSettings.put(settingKey, Long.valueOf(value));
        else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            providerSettings.put(settingKey, Boolean.valueOf(value));
        } else {
            // 如果是不支持的类型，则不执行任何操作并直接返回
            return;
        }

        log.info(
            "The setting has been override by key: {}, value: {}, in {} provider of {} module through {}", settingKey,
            value, providerName, moduleName, "System.properties"
        );
    }
}
