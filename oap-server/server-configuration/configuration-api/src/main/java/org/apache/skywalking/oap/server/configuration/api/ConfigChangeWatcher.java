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

package org.apache.skywalking.oap.server.configuration.api;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * ConfigChangeWatcher represents a watcher implementor, it will be called when the target value changed.
 * <pre>
 * (ConfigChangeWatcher 表示一个 watcher 实现器，当目标值发生变化时，它会被调用。)
 *
 * 配置变更观察者
 * </pre>
 */
@Getter
public abstract class ConfigChangeWatcher {
    /** 模块 */
    private final String module;
    /** 模块提供者 */
    private final ModuleProvider provider;
    /** 观察的项目名 */
    private final String itemName;
    /** 观察类型 */
    protected WatchType watchType;

    /**
     * <pre>
     * 构造 watcher 实例。
     *
     * WatchType 默认是 SINGLE。
     * </pre>
     *
     * @param module
     * @param provider
     * @param itemName
     */
    public ConfigChangeWatcher(String module, ModuleProvider provider, String itemName) {
        this.module = module;
        this.provider = provider;
        this.itemName = itemName;
        this.watchType = WatchType.SINGLE;
    }

    /**
     * Notify the watcher, the new value received.
     * <pre>
     * (通知 观察者 收到新值。)
     * </pre>
     *
     * @param value of new.
     */
    public abstract void notify(ConfigChangeEvent value);

    /**
     * @return current value of current config.
     */
    public abstract String value();

    @Override
    public String toString() {
        return "ConfigChangeWatcher{" + "module=" + module + ", provider=" + provider + ", itemName='" + itemName + '\'' + '}';
    }

    /** 配置变更事件 */
    @Setter(AccessLevel.PACKAGE)
    @Getter
    public static class ConfigChangeEvent {
        /** 新值 */
        private String newValue;
        /** 事件类型 */
        private EventType eventType;

        public ConfigChangeEvent(String newValue, EventType eventType) {
            this.newValue = newValue;
            this.eventType = eventType;
        }
    }

    /** 事件类型 */
    public enum EventType {
        ADD, MODIFY, DELETE
    }

    /** 观察类型 */
    public enum WatchType {
        /** ConfigChangeWatcher 默认是 SINGLE 类型 */
        SINGLE,

        /** {@link GroupConfigChangeWatcher} */
        GROUP
    }
}
