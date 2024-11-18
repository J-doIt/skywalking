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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.ssl.SslContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

    /** SSL上下文 */
    private final SslContext sslCtx;

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            // 如果SSL上下文不为空，则添加SSL处理器
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }
        p.addLast(new HttpServerCodec()); // netty的http编解码器
        // HttpServerExpectContinueHandler 用于处理 HTTP 请求中的 Expect: 100-continue 机制。
        // 这个机制在 HTTP/1.1 中定义，主要用于客户端在发送大请求体之前，先询问服务器是否愿意接收请求体。
        // 如果服务器愿意接收，它会返回一个 100 Continue 响应，客户端才会继续发送请求体。
        p.addLast(new HttpServerExpectContinueHandler());
        p.addLast(new HttpServerHandler()); // sw.prometheus 的HTTP服务器处理器
    }
}
