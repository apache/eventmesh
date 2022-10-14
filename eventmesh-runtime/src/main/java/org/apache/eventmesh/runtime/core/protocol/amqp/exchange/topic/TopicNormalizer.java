/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.eventmesh.runtime.core.protocol.amqp.exchange.topic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TopicNormalizer {

    private static final String STAR_TOKEN = "*";
    private static final String HASH_TOKEN = "#";
    private static final String SEPARATOR = ".";

    private TopicNormalizer() {
    }

    public static String normalize(String routingKey) {
        if (routingKey == null) {
            return "";
        } else if (!(routingKey.contains(HASH_TOKEN) || !routingKey.contains(STAR_TOKEN))) {
            return routingKey;
        } else {
            List<String> subscriptionList = new ArrayList<String>(Arrays.asList(routingKey.split("\\.")));

            int size = subscriptionList.size();

            for (int index = 0; index < size; index++) {
                // if there are more levels
                if ((index + 1) < size) {
                    if (subscriptionList.get(index).equals(HASH_TOKEN)) {
                        if (subscriptionList.get(index + 1).equals(HASH_TOKEN)) {
                            // we don't need #.# delete this one
                            subscriptionList.remove(index);
                            size--;
                            // redo this normalisation
                            index--;
                        }

                        if (subscriptionList.get(index + 1).equals(STAR_TOKEN)) {
                            // we don't want #.* swap to *.#
                            // remove it and put it in at index + 1
                            subscriptionList.add(index + 1, subscriptionList.remove(index));
                        }
                    }
                } // if we have more levels
            }

            Iterator<String> iter = subscriptionList.iterator();
            StringBuilder builder = new StringBuilder(iter.next());
            while (iter.hasNext()) {
                builder.append(SEPARATOR).append(iter.next());
            }
            return builder.toString();
        }
    }

}
