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
 */

package org.apache.eventmeth.protocol.http.event;

import org.apache.eventmeth.protocol.http.consumer.ConsumerGroupConf;

public class ConsumerGroupStateEvent {

    public String                   consumerGroup;
    public ConsumerGroupStateAction action;
    public ConsumerGroupConf        consumerGroupConfig;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("consumerGroupStateEvent={")
                .append("consumerGroup=").append(consumerGroup)
                .append(",action=").append(action).append("}");
        return sb.toString();
    }


    public enum ConsumerGroupStateAction {
        NEW,
        CHANGE,
        DELETE
    }
}
