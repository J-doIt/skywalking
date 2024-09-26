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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The <code>ModuleManager</code> takes charge of all {@link ModuleDefine}s in collector.
 *
 * <pre>
 * (ModuleManager 负责 collector 中的所有 ModuleDefine 。)
 *
 * 组件管理器，负责组件的管理与初始化。
 * </pre>
 */
public class ModuleManager implements ModuleDefineHolder {
    private boolean isInPrepareStage = true;
    /** ≤ 模块名，模块定义 ≥ */
    private final Map<String, ModuleDefine> loadedModules = new HashMap<>();

    /**
     * Init the given modules
     *
     * <pre>
     * 初始化应用模块，负责加载并启动所有配置的模块。
     * 这个过程包括模块的准备（prepare）、启动（start）以及完成后的通知（notifyAfterCompleted）。
     * </pre>
     */
    public void init(
        ApplicationConfiguration applicationConfiguration) throws ModuleNotFoundException, ProviderNotFoundException, ServiceNotProvidedException, CycleDependencyException, ModuleConfigException, ModuleStartException {

        // 获取配置中的模块列表
        String[] moduleNames = applicationConfiguration.moduleList();
        // 使用 Java SPI 机制加载所有可用的 ModuleDefine 和 ModuleProvider
        ServiceLoader<ModuleDefine> moduleServiceLoader = ServiceLoader.load(ModuleDefine.class);
        ServiceLoader<ModuleProvider> moduleProviderLoader = ServiceLoader.load(ModuleProvider.class);

        HashSet<String> moduleSet = new HashSet<>(Arrays.asList(moduleNames));
        // 遍历加载的ModuleDefine，对配置中列出的模块进行准备阶段的操作
        for (ModuleDefine module : moduleServiceLoader) {
            if (moduleSet.contains(module.name())) {
                /* 准备模块 */
                // 准备模块，传入 模块管理、模块配置、ModuleProvider加载器
                module.prepare(this, applicationConfiguration.getModuleConfiguration(module.name()), moduleProviderLoader);
                // 记录已加载的模块
                loadedModules.put(module.name(), module);
                // 完成准备的模块从待处理集合中移除
                moduleSet.remove(module.name());
            }
        }
        // Finish prepare stage
        // 标记准备阶段结束
        isInPrepareStage = false;

        // 检查是否有配置的模块未能加载，如有则抛出异常
        if (moduleSet.size() > 0) {
            throw new ModuleNotFoundException(moduleSet.toString() + " missing.");
        }

        // 创建启动流程实例，组织模块的启动顺序
        BootstrapFlow bootstrapFlow = new BootstrapFlow(loadedModules);

        /* 开始执行启动流程 */
        bootstrapFlow.start(this);
        /* 所有模块启动完成后通知 */
        bootstrapFlow.notifyAfterCompleted();
    }

    @Override
    public boolean has(String moduleName) {
        return loadedModules.get(moduleName) != null;
    }

    @Override
    public ModuleProviderHolder find(String moduleName) throws ModuleNotFoundRuntimeException {
        assertPreparedStage();
        ModuleDefine module = loadedModules.get(moduleName);
        if (module != null)
            return module;
        throw new ModuleNotFoundRuntimeException(moduleName + " missing.");
    }

    private void assertPreparedStage() {
        if (isInPrepareStage) {
            throw new AssertionError("Still in preparing stage.");
        }
    }
}
