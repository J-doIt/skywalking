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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.util.StringBuilderWriter;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * <pre>
 * 简单通道入栈处理器的实现类，接收 HttpObject 类型的消息。
 * 用于将 注册表（CollectorRegistry） 中的 指标数据，发送给 Prometheus。
 * </pre>
 */
@Slf4j
public class HttpServerHandler  extends SimpleChannelInboundHandler<HttpObject> {

    /** prometheus 的用于管理和注册指标的收集器 */
    private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;
    /** 写缓冲区 */
    private final StringBuilderWriter buf = new StringBuilderWriter();

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        // 将缓冲区中的数据立即写出到对端
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            // 判断请求是否需要保持连接
            boolean keepAlive = HttpUtil.isKeepAlive(req);

            // 清空缓冲区
            buf.getBuilder().setLength(0);
            try {
                // 将 注册表 中的 指标数据 写入缓冲区
                TextFormat.write004(buf, registry.metricFamilySamples());
            } catch (IOException e) {
                ctx.fireExceptionCaught(e);
                return;
            }
            // 创建响应对象，设置状态码为 200 OK，并将 缓冲区中的内容 作为 响应体
            FullHttpResponse response = new DefaultFullHttpResponse(req.protocolVersion(), OK,
                Unpooled.copiedBuffer(buf.getBuilder().toString(), CharsetUtil.UTF_8));
            // 设置响应头
            response.headers()
                .set(CONTENT_TYPE, TEXT_PLAIN) // 设置内容类型为纯文本
                .setInt(CONTENT_LENGTH, response.content().readableBytes()); // 设置内容长度

            if (keepAlive) {
                // 如果请求版本不支持默认的连接保持，则显式设置 Connection: keep-alive
                if (!req.protocolVersion().isKeepAliveDefault()) {
                    response.headers().set(CONNECTION, KEEP_ALIVE);
                }
            } else {
                // Tell the client we're going to close the connection.
                // 如果不需要保持连接，设置 Connection: close
                response.headers().set(CONNECTION, CLOSE);
            }

            // 写入响应
            ChannelFuture f = ctx.write(response);

            // 如果不需要保持连接，则在写入完成后关闭连接
            if (!keepAlive) {
                f.addListener(CLOSE);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Prometheus exporter error", cause);
        // 创建失败的应答
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            INTERNAL_SERVER_ERROR,
            Unpooled.wrappedBuffer(cause.getMessage().getBytes()));
        // 将 response 写回客户端，
        // 并添加一个 ChannelFutureListener，用于关闭 指定 ChannelFuture（异步Channel I/O 操作的结果） 关联的 Channel。
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        ctx.close();
    }
}