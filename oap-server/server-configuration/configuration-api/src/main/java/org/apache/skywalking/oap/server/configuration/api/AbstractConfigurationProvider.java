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

package org.apache.skywalking.oap.server.configuration.api;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * The recommendation default base implementor of Configuration module. The real implementor could extend this provider
 * to make a new one, easily.
 * <pre>
 * (Configuration模块的推荐默认基本实现器。真正的实现者可以很容易地扩展此提供程序以创建新的提供程序。)
 * </pre>
 */
public abstract class AbstractConfigurationProvider extends ModuleProvider {
    /** 配置观察者注册服务 */
    private ConfigWatcherRegister configWatcherRegister;

    @Override
    public Class<? extends ModuleDefine> module() {
        // 模块：configuration
        return ConfigurationModule.class;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        // Provider 的 prepare 阶段：

        // 根据 configuration.xxx 配置，初始化 ConfigWatcherRegister 实现类
        configWatcherRegister = initConfigReader();
        // 为 "配置模块" 的 Provider 注册 DynamicConfigurationService 的实现类
        this.registerServiceImplementation(DynamicConfigurationService.class, configWatcherRegister);
    }

    /** 根据 configuration.xxx 配置，初始化 ConfigWatcherRegister 实现类 */
    protected abstract ConfigWatcherRegister initConfigReader() throws ModuleStartException;

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        // Provider 的 start 阶段结束后：

        // 启动 配置观察者注册服务
        configWatcherRegister.start();
    }

    @Override
    public String[] requiredModules() {
        // 该模块的必要模块：
        return new String[0];
    }

}
