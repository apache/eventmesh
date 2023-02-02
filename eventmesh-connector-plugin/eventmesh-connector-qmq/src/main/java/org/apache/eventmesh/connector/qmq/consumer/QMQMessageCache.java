/**
 * Copyright (C) @2022 Webank Group Holding Limited
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.eventmesh.connector.qmq.consumer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import qunar.tc.qmq.Message;

public class QMQMessageCache {
    private static final Map<String, Message> messageMap = new ConcurrentHashMap();

    public QMQMessageCache() {

    }

    public void putMessage(String id, Message message) {
        messageMap.put(id, message);
    }

    public Message removeMessage(String id) {
        return messageMap.remove(id);
    }

    public Set<Map.Entry<String, Message>> entrySet() {
        return messageMap.entrySet();
    }
}
