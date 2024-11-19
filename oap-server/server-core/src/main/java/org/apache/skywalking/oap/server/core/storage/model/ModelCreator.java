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

package org.apache.skywalking.oap.server.core.storage.model;

import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * INewModel implementation supports creating a new module.
 * <pre>
 * (INewModel 的实现类 支持创建新模块。)
 * </pre>
 */
public interface ModelCreator extends Service {

    /**
     * 添加新 model
     * @return the created new model
     */
    Model add(Class<?> aClass, int scopeId, Storage storage, boolean record) throws StorageException;

    /** 添加 CreatingListener */
    void addModelListener(CreatingListener listener) throws StorageException;

    /** 执行 this.add() 或 this.addModelListener() 会得到通知 */
    interface CreatingListener {
        void whenCreating(Model model) throws StorageException;
    }
}
