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

package org.apache.skywalking.oap.server.starter;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.starter.config.ApplicationConfigLoader;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Starter core. Load the core configuration file, and initialize the startup sequence through {@link ModuleManager}.
 *
 * <pre>
 * (core 的 启动器。加载 core 的 配置文件，通过 ModuleManager 按顺序 初始化 和 启动。)
 * </pre>
 */
@Slf4j
public class OAPServerBootstrap {
    public static void start() {
        // 获取系统属性"mode"，设置运行模式到RunningMode中
        String mode = System.getProperty("mode");
        RunningMode.setMode(mode);

        // 初始化 配置加载器
        ApplicationConfigLoader configLoader = new ApplicationConfigLoader();
        // 初始化 模块管理器
        ModuleManager manager = new ModuleManager();
        try {
            /* 加载 应用程序配置 */
            ApplicationConfiguration applicationConfiguration = configLoader.load();
            /* 初始化 模块管理器 */
            manager.init(applicationConfiguration);

            // 从 模块管理器 中找到 Telemetry 模块，获取 MetricsCreator 服务
            manager.find(TelemetryModule.NAME)
                   .provider()
                   .getService(MetricsCreator.class)
                    // 创建一个名为 "uptime" 的 Gauge（实时数据指标），用于记录OAP服务器启动时间
                   .createGauge("uptime", "oap server start up time", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE)
                   // Set uptime to second
                   // 设置 Gauge 的值为当前时间（毫秒）转换为秒
                   .setValue(System.currentTimeMillis() / 1000d);

            // 如果运行模式是初始化模式，则打印日志并退出程序
            if (RunningMode.isInitMode()) {
                log.info("OAP starts up in init mode successfully, exit now...");
                System.exit(0);
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            System.exit(1);
        }
    }
}
