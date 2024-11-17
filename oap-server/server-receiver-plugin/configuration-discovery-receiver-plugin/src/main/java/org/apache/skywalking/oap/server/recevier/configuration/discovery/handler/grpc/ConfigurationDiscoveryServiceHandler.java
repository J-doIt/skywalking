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

package org.apache.skywalking.oap.server.recevier.configuration.discovery.handler.grpc;

import com.google.common.collect.Lists;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationSyncRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.AgentConfigurations;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.AgentConfigurationsWatcher;

/**
 * Provide query agent dynamic configuration, through the gRPC protocol,
 */
@Slf4j
public class ConfigurationDiscoveryServiceHandler extends ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceImplBase implements GRPCHandler {

    /** 配置变更观察者 */
    private final AgentConfigurationsWatcher agentConfigurationsWatcher;

    /**
     * If the current configuration is true, the requestId and uuid will not be judged, and the dynamic configuration of
     * the service corresponding to the agent will be returned directly
     * <pre>
     * (如果当前配置为 true，则不会判断 requestId 和 uuid，直接返回 agent 对应的服务的动态配置)
     * </pre>
     */
    private boolean disableMessageDigest = false;

    public ConfigurationDiscoveryServiceHandler(AgentConfigurationsWatcher agentConfigurationsWatcher,
                                                boolean disableMessageDigest) {
        this.agentConfigurationsWatcher = agentConfigurationsWatcher;
        this.disableMessageDigest = disableMessageDigest;
    }

    /*
     * Process the request for querying the dynamic configuration of the agent.
     * If there is agent dynamic configuration information corresponding to the service,
     * the ConfigurationDiscoveryCommand is returned to represent the dynamic configuration information.
     * （处理查询 Agent 动态配置的请求。
     * 如果存在与服务对应的 Agent 动态配置信息，则返回 ConfigurationDiscoveryCommand 来表示动态配置信息。）
     */
    @Override
    public void fetchConfigurations(final ConfigurationSyncRequest request,
                                    final StreamObserver<Commands> responseObserver) {
        // 构建通过grpc传输的Commands
        Commands.Builder commandsBuilder = Commands.newBuilder();

        // 从 AgentConfigurationsWatcher 拿到 服务的动态配置信息。
        AgentConfigurations agentConfigurations = agentConfigurationsWatcher.getAgentConfigurations(
            request.getService());
        if (null != agentConfigurations) {
            // 禁用消息摘要 或者 uuid（消息摘要）有变化
            if (disableMessageDigest || !Objects.equals(agentConfigurations.getUuid(), request.getUuid())) {
                // 创建 ConfigurationDiscoveryCommand
                ConfigurationDiscoveryCommand configurationDiscoveryCommand =
                    newAgentDynamicConfigCommand(agentConfigurations);
                // 序列化 configurationDiscoveryCommand 后，add 到 commandsBuilder。
                commandsBuilder.addCommands(configurationDiscoveryCommand.serialize().build());
            }
        }
        // 使用 stream 发送 Commands 到 Agent
        responseObserver.onNext(commandsBuilder.build());
        // stream 完成
        responseObserver.onCompleted();
    }

    /**
     * 创建 ConfigurationDiscoveryCommand，用于返回给 Agent 去执行。
     * @param agentConfigurations Agent动态配置
     * @return
     */
    public ConfigurationDiscoveryCommand newAgentDynamicConfigCommand(AgentConfigurations agentConfigurations) {
        // 构建 ConfigurationDiscoveryCommand 的 key-value
        List<KeyStringValuePair> configurationList = Lists.newArrayList();
        agentConfigurations.getConfiguration().forEach((k, v) -> {
            KeyStringValuePair.Builder builder = KeyStringValuePair.newBuilder().setKey(k).setValue(v);
            configurationList.add(builder.build());
        });
        // 创建 ConfigurationDiscoveryCommand
        return new ConfigurationDiscoveryCommand(
            UUID.randomUUID().toString(), agentConfigurations.getUuid(), configurationList);
    }
}
