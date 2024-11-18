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

package org.apache.skywalking.oap.server.telemetry.prometheus.httpserver;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.server.ssl.HttpDynamicSslContext;
import org.apache.skywalking.oap.server.telemetry.prometheus.PrometheusConfig;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 * <pre>
 * (一个 HTTP 服务器，它以非常纯文本的形式 sends back 收到的 HTTP 请求的内容。)
 * 一个Http服务，暴露Prometheus监控指标的端口。这使得外部监控系统（Prometheus）可以拉取 OAP收集到的指标数据。
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public final class HttpServer {

    /** Prometheus 的 ModuleConfig */
    private final PrometheusConfig config;

    public void start() throws InterruptedException {
        // Configure SSL.
        // （配置SSL）
        final HttpDynamicSslContext sslCtx;
        if (config.isSslEnabled()) {
            // 如果启用了SSL，则使用配置的密钥和证书链路径创建SSL上下文
            sslCtx = HttpDynamicSslContext.forServer(config.getSslKeyPath(), config.getSslCertChainPath());
        } else {
            sslCtx = null;
        }

        // Configure the server.
        // 通过netty配置服务器

        ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true).build(); // 创建一个守护线程工厂
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, tf); // 创建Boss线程组，用于处理新连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(0, tf); // 创建Worker线程组，用于处理I/O操作
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class) // 设置NIO通道
            .handler(new LoggingHandler(LogLevel.INFO)) // 添加日志处理器
            .childHandler(new HttpServerInitializer(sslCtx)); // 使用 ChannelInitializer 定义子处理器

        // 绑定服务器到指定的主机和端口，并同步等待绑定完成
        b.bind(config.getHost(), config.getPort()).sync();
        // 如果SSL上下文不为空，则启动SSL上下文
        Optional.ofNullable(sslCtx).ifPresent(HttpDynamicSslContext::start);

        log.info("Prometheus exporter endpoint:" +
            (config.isSslEnabled() ? "https" : "http") + "://" + config.getHost() + ":" + config.getPort() + '/');
    }
}
