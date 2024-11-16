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

/**
 * Configuration Module sync the settings from remote service, the remote service could be implemented by this module
 * provider.
 * <p>
 * Any configuration item in the whole OAP backend could register a watcher to configuration module, the item change
 * watcher will be called, if the value changed.
 *
 * <pre>
 * (配置模块从远程服务同步设置，远程服务可以由该模块提供商实现。
 * 整个 OAP 后端中的任何配置项都可以向配置模块注册一个 watcher，如果值发生变化，则会调用 item change watcher。)
 * </pre>
 */
public class ConfigurationModule extends ModuleDefine {
    /** 模块名 */
    public static final String NAME = "configuration";

    public ConfigurationModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        // 动态配置服务接口
        return new Class[] {DynamicConfigurationService.class};
    }
}
