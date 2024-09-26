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

package org.apache.skywalking.oap.server.library.module;

import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A module definition.
 *
 * <pre>
 * 模块定义
 * </pre>
 */
public abstract class ModuleDefine implements ModuleProviderHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleDefine.class);

    /** 组件服务提供者 */
    private ModuleProvider loadedProvider = null;

    /** 组件名 */
    private final String name;

    public ModuleDefine(String name) {
        this.name = name;
    }

    /**
     * @return the module name
     *
     */
    public final String name() {
        return name;
    }

    /**
     * <pre>
     * 返回该模块提供的服务。
     * 这些服务会被模块管理系统管理和发现，供其他模块使用或扩展。
     * </pre>
     *
     * @return the {@link Service} provided by this module.
     */
    public abstract Class[] services();

    /**
     * Run the prepare stage for the module, including finding all potential providers, and asking them to prepare.
     * <pre>
     * (运行模块的准备阶段，包括找到所有可能的提供者，并要求他们准备。)
     *
     * 准备模块，这个方法负责初始化模块的提供者(provider)，检查配置，并执行必要的设置。
     * 它会遍历可用的ModuleProvider，基于配置选择合适的提供者，处理可能的重复提供者错误，
     * 并最终调用选中提供者的准备方法来完成模块的准备阶段。
     * </pre>
     *
     * @param moduleManager of this module 当前应用的模块管理器，用于模块间交互。
     * @param configuration of this module 本模块的特定配置信息。
     * @param moduleProviderLoader 加载ModuleProvider的服务加载器，用于发现所有可用的模块提供者。
     * @throws ProviderNotFoundException when even don't find a single one providers.
     */
    void prepare(ModuleManager moduleManager, ApplicationConfiguration.ModuleConfiguration configuration,
        ServiceLoader<ModuleProvider> moduleProviderLoader) throws ProviderNotFoundException, ServiceNotProvidedException, ModuleConfigException, ModuleStartException {

        // 遍历所有可用的 ModuleProvider
        for (ModuleProvider provider : moduleProviderLoader) {
            // 检查配置中是否包含了当前 provider 的定义
            if (!configuration.has(provider.name())) {
                continue;
            }

            // 确保 provider 与 当前 模块定义 相匹配
            if (provider.module().equals(getClass())) {
                // 防止同一模块有多个provider定义
                if (loadedProvider == null) {
                    loadedProvider = provider;
                    loadedProvider.setManager(moduleManager);
                    loadedProvider.setModuleDefine(this);
                } else {
                    // 若已加载provider，则抛出异常表示重复定义
                    throw new DuplicateProviderException(this.name() + " module has one " + loadedProvider.name() + "[" + loadedProvider
                        .getClass()
                        .getName() + "] provider already, " + provider.name() + "[" + provider.getClass()
                                                                                              .getName() + "] is defined as 2nd provider.");
                }
            }

        }

        // 如果没有找到合适的provider，则抛出异常
        if (loadedProvider == null) {
            throw new ProviderNotFoundException(this.name() + " module no provider found.");
        }

        LOGGER.info("Prepare the {} provider in {} module.", loadedProvider.name(), this.name());
        try {
            // 尝试将 配置信息 映射到 provider 的 配置bean 上
            copyProperties(loadedProvider.createConfigBeanIfAbsent(), configuration.getProviderConfiguration(loadedProvider
                .name()), this.name(), loadedProvider.name());
        } catch (IllegalAccessException e) {
            throw new ModuleConfigException(this.name() + " module config transport to config bean failure.", e);
        }
        /* provider.prepare，开始模块的准备流程 */
        loadedProvider.prepare();
    }

    private void copyProperties(ModuleConfig dest, Properties src, String moduleName,
        String providerName) throws IllegalAccessException {
        if (dest == null) {
            return;
        }
        Enumeration<?> propertyNames = src.propertyNames();
        while (propertyNames.hasMoreElements()) {
            String propertyName = (String) propertyNames.nextElement();
            Class<? extends ModuleConfig> destClass = dest.getClass();
            try {
                Field field = getDeclaredField(destClass, propertyName);
                field.setAccessible(true);
                field.set(dest, src.get(propertyName));
            } catch (NoSuchFieldException e) {
                LOGGER.warn(propertyName + " setting is not supported in " + providerName + " provider of " + moduleName + " module");
            }
        }
    }

    private Field getDeclaredField(Class<?> destClass, String fieldName) throws NoSuchFieldException {
        if (destClass != null) {
            Field[] fields = destClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            return getDeclaredField(destClass.getSuperclass(), fieldName);
        }

        throw new NoSuchFieldException();
    }

    @Override
    public final ModuleProvider provider() throws DuplicateProviderException, ProviderNotFoundException {
        if (loadedProvider == null) {
            throw new ProviderNotFoundException("There is no module provider in " + this.name() + " module!");
        }

        return loadedProvider;
    }
}
