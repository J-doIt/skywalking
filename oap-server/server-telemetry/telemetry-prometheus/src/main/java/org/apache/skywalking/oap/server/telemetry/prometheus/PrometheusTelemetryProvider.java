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

package org.apache.skywalking.oap.server.telemetry.prometheus;

import io.prometheus.client.hotspot.DefaultExports;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.prometheus.httpserver.HttpServer;

/**
 * Start the Prometheus
 * <pre>
 * (启动 Prometheus)
 * </pre>
 */
public class PrometheusTelemetryProvider extends ModuleProvider {

    /** Prometheus 的 ModuleConfig */
    private PrometheusConfig config;

    public PrometheusTelemetryProvider() {
        config = new PrometheusConfig();
    }

    @Override
    public String name() {
        return "prometheus";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        // 遥测模块
        return TelemetryModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        // Provider 的 prepare 阶段：

        // 注册 MetricsCreator 接口的实现类 PrometheusMetricsCreator 到 Prometheus服务提供者 中
        this.registerServiceImplementation(MetricsCreator.class, new PrometheusMetricsCreator());
        // 注册 MetricsCollector 接口的实现类 PrometheusMetricsCollector 到 Prometheus服务提供者 中
        this.registerServiceImplementation(MetricsCollector.class, new PrometheusMetricsCollector());
        try {
            // 初始化并启动一个HTTP服务器，暴露Prometheus监控指标的端口
            new HttpServer(config).start();
        } catch (InterruptedException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        // 初始化默认的导出器(DefaultExports)，这通常是用来自动导出JVM的默认指标到Prometheus的注册表中。
        // 这一步有助于收集关于JVM性能（如内存、线程等）的基本指标。
        DefaultExports.initialize();
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
