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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class SharingServerConfig extends ModuleConfig {
    private String restHost;
    /**
     * Only setting the real port(not 0) makes the jetty server online.
     * <pre>
     * (只有设置真实端口（不是 0）才能使 jetty 服务器 在线。)
     * </pre>
     */
    private int restPort;
    private String restContextPath;
    private int restMinThreads = 1;
    private int restMaxThreads = 200;
    private long restIdleTimeOut = 30000;
    private int restAcceptorPriorityDelta = 0;
    private int restAcceptQueueSize = 0;

    private String gRPCHost;
    /**
     * Only setting the real port(not 0) makes the gRPC server online.
     * <pre>
     * (只有设置真实端口（不是 0）才会使 gRPC服务器 在线。)
     * </pre>
     */
    private int gRPCPort;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private int gRPCThreadPoolSize;
    private int gRPCThreadPoolQueueSize;
    /** 用于身份验证的令牌文本。仅适用于 gRPC 连接。设置后，客户端需要使用相同的令牌。 */
    private String authentication;
    /** 为 gRPC 服务激活 SSL。 */
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath;
    private String gRPCSslCertChainPath;
    private String gRPCSslTrustedCAsPath;

    /**
     * The maximum size in bytes allowed for request headers.
     * Use -1 to disable it.
     */
    private int httpMaxRequestHeaderSize = 8192;
}
