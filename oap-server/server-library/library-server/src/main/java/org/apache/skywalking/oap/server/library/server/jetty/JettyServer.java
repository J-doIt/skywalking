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

package org.apache.skywalking.oap.server.library.server.jetty;

import java.util.Objects;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyServer implements Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(JettyServer.class);

    /** jetty Server */
    private org.eclipse.jetty.server.Server server;
    private ServletContextHandler servletContextHandler;
    private JettyServerConfig jettyServerConfig;

    public JettyServer(JettyServerConfig config) {
        this.jettyServerConfig = config;
    }

    @Override
    public String hostPort() {
        return jettyServerConfig.getHost() + ":" + jettyServerConfig.getPort();
    }

    @Override
    public String serverClassify() {
        return "Jetty";
    }

    /**
     * 初始化Jetty服务器，根据配置项设置线程池、HTTP配置、连接器参数以及Servlet上下文。
     */
    @Override
    public void initialize() {

        // 创建QueuedThreadPool线程池，用于管理Jetty服务器的工作线程
        QueuedThreadPool threadPool = new QueuedThreadPool();
        // 设置线程池最小和最大线程数，源自Jetty服务器配置
        threadPool.setMinThreads(jettyServerConfig.getJettyMinThreads());
        threadPool.setMaxThreads(jettyServerConfig.getJettyMaxThreads());

        // 创建Jetty服务器实例，使用自定义的线程池
        server = new org.eclipse.jetty.server.Server(threadPool);

        // 配置HTTP请求的相关设置，如最大请求头大小
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setRequestHeaderSize(jettyServerConfig.getJettyHttpMaxRequestHeaderSize());

        // 创建ServerConnector以定义服务器如何接收连接，使用指定的HTTP配置
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        connector.setHost(jettyServerConfig.getHost());
        connector.setPort(jettyServerConfig.getPort());
        connector.setIdleTimeout(jettyServerConfig.getJettyIdleTimeOut()); // 设置连接器的空闲超时时间
        connector.setAcceptorPriorityDelta(jettyServerConfig.getJettyAcceptorPriorityDelta());
        connector.setAcceptQueueSize(jettyServerConfig.getJettyAcceptQueueSize()); // 接受队列大小
        // 将配置好的连接器添加到服务器
        server.setConnectors(new Connector[] {connector});

        // 初始化ServletContextHandler，用于管理Servlet上下文，不支持会话
        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        // 设置上下文路径
        servletContextHandler.setContextPath(jettyServerConfig.getContextPath());
        LOGGER.info("http server root context path: {}", jettyServerConfig.getContextPath());

        // 将Servlet上下文处理器设置为服务器的主处理器
        server.setHandler(servletContextHandler);

        // 创建默认的Jetty处理程序实例
        JettyDefaultHandler defaultHandler = new JettyDefaultHandler();
        // 创建ServletHolder用于持有默认处理程序的实例
        ServletHolder defaultHolder = new ServletHolder();
        defaultHolder.setServlet(defaultHandler);

        // 将默认Servlet添加到Servlet上下文中，使用其指定的路径规范
        servletContextHandler.addServlet(defaultHolder, defaultHandler.pathSpec());
    }

    public void addHandler(JettyHandler handler) {
        LOGGER.info(
            "Bind handler {} into jetty server {}:{}",
            handler.getClass().getSimpleName(), jettyServerConfig.getHost(), jettyServerConfig.getPort()
        );

        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet(handler);
        servletContextHandler.addServlet(servletHolder, handler.pathSpec());
    }

    @Override
    public boolean isSSLOpen() {
        return false;
    }

    @Override
    public boolean isStatusEqual(Server target) {
        return equals(target);
    }

    @Override
    public void start() throws ServerException {
        LOGGER.info("start server, host: {}, port: {}", jettyServerConfig.getHost(), jettyServerConfig.getPort());
        try {
            if (LOGGER.isDebugEnabled()) {
                if (servletContextHandler.getServletHandler() != null && servletContextHandler.getServletHandler()
                                                                                              .getServletMappings() != null) {
                    for (ServletMapping servletMapping : servletContextHandler.getServletHandler()
                                                                              .getServletMappings()) {
                        LOGGER.debug(
                            "jetty servlet mappings: {} register by {}", servletMapping.getPathSpecs(), servletMapping
                                .getServletName());
                    }
                }
            }

            // jetty server 启动
            server.start();
        } catch (Exception e) {
            throw new JettyServerException(e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        JettyServer that = (JettyServer) o;
        return jettyServerConfig.getPort() == that.jettyServerConfig.getPort() && Objects.equals(
            jettyServerConfig.getHost(), that.jettyServerConfig.getHost());
    }

    @Override
    public int hashCode() {
        return Objects.hash(jettyServerConfig.getHost(), jettyServerConfig.getPort());
    }

}
