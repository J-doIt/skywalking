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

import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Sharing server is an independent gRPC and Jetty servers provided for all receiver modules. In default, this module
 * would not be activated unless the user active explicitly. It only delegates the core gRPC and Jetty servers.
 *
 * Once it is activated, provides separated servers, then all receivers use these to accept outside requests. Typical,
 * this is activated to avoid the ip, port and thread pool sharing between receiver and internal traffics. For security
 * consideration, receiver should open TLS and token check, and internal(remote module) traffic should base on trusted
 * network, no TLS and token check. Even some companies may require TLS internally, it still use different TLS keys. In
 * this specific case, we recommend users to consider use {@link org.apache.skywalking.oap.server.core.CoreModuleConfig.Role}.
 *
 * <pre>
 * (共享服务器 是为 所有接收模块 提供 独立的gRPC和Jetty服务器。
 *
 * 默认情况下，除非用户显式激活，否则该模块不会被激活。它只委托核心gRPC和Jetty服务器。
 * 一旦它被激活，就会提供独立的服务器，然后所有接收方都使用这些服务器来接受外部请求。
 *
 * 通常，激活此选项是为了避免在接收端和内部通信流之间共享ip、端口和线程池。
 * 出于安全考虑，接收方应打开TLS和令牌检查，内部（远程模块）流量应基于可信网络，不进行TLS和令牌检查。
 * 即使一些公司内部可能需要TLS，它仍然使用不同的TLS密钥。在这种特殊情况下，我们建议用户考虑使用 CoreModuleConfig.Role。)
 *
 * 共享服务模块（“接收器”共享“内部（CoreModule模块）”的gRPC和Jetty服务）
 * </pre>
 */
public class SharingServerModule extends ModuleDefine {

    public static final String NAME = "receiver-sharing-server";

    public SharingServerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        // 该模块提供的服务：
        return new Class[] {
            GRPCHandlerRegister.class, // GrpcHandler注册服务
            JettyHandlerRegister.class // JettyHandler注册服务
        };
    }
}
