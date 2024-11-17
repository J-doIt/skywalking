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

import lombok.Setter;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;

/**
 * Provider 的 prepare 阶段时，Provider的jetty服务为空时，作为 JettyHandlerRegister 实现类。
 */
public class ReceiverJettyHandlerRegister implements JettyHandlerRegister {

    /**
     * Provider 的 start 阶段，将 CoreModule提供者 的 JettyHandlerRegister 实现类设置给它。
     */
    @Setter
    private JettyHandlerRegister jettyHandlerRegister;

    @Override
    public void addHandler(JettyHandler serverHandler) {
        jettyHandlerRegister.addHandler(serverHandler);
    }
}
