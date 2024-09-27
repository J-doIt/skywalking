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

package org.apache.skywalking.oap.server.cluster.plugin.standalone;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;

/**
 * A cluster manager simulator. Work in memory only. Also return the current instance.
 * <pre>
 * (集群管理器模拟器。仅在内存中工作。同时返回当前实例。)
 * </pre>
 */
public class StandaloneManager implements ClusterNodesQuery, ClusterRegister {

    /** 保存本地节点的RemoteInstance信息，使用volatile保证多线程环境下的可见性。 */
    private volatile RemoteInstance remoteInstance;

    /**
     * 实现ClusterRegister接口方法，用于注册远程实例到当前管理器。
     * 在单机模式下，直接将传入的实例设为本地实例，并标记为自我实例。
     *
     * @param remoteInstance 要注册的远程实例信息
     */
    @Override
    public void registerRemote(RemoteInstance remoteInstance) {
        this.remoteInstance = remoteInstance;
        // 标记此远程实例为本机实例
        this.remoteInstance.getAddress().setSelf(true);
    }

    /**
     * 实现ClusterNodesQuery接口方法，查询当前集群中的所有远程节点实例。
     * 在单机部署模式下，仅返回包含本地节点的列表。
     *
     * @return List<RemoteInstance> 当前集群中所有节点的列表，在单机模式下仅为包含单个本地节点的列表
     */
    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        // 如果本地节点信息尚未注册，则返回一个空列表
        if (remoteInstance == null) {
            return new ArrayList(0);
        }
        ArrayList remoteList = new ArrayList(1);
        remoteList.add(remoteInstance);

        // 返回包含单个本地节点的列表
        return remoteList;
    }
}
