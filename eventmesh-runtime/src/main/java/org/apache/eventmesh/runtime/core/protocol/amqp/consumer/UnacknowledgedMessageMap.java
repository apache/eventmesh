/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpChannel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;

/**
 * unack message map.
 */
public class UnacknowledgedMessageMap {

    /**
     * unAck positionInfo.
     */
    public static final class MessageConsumerAssociation {
        private final String messageId;
        private final AmqpConsumer consumer;
        private final int size;

        private MessageConsumerAssociation(String messageId, AmqpConsumer consumer, int size) {
            this.messageId = messageId;
            this.consumer = consumer;
            this.size = size;
        }

        public String getMessageId() {
            return messageId;
        }

        public AmqpConsumer getConsumer() {
            return consumer;
        }

        public int getSize() {
            return size;
        }
    }

    private final Map<Long, MessageConsumerAssociation> map = new ConcurrentHashMap<>();
    private final AmqpChannel channel;

    public UnacknowledgedMessageMap(AmqpChannel channel) {
        this.channel = channel;
    }

    public Collection<MessageConsumerAssociation> acknowledge(long deliveryTag, boolean multiple) {
        if (multiple) {
            Map<Long, MessageConsumerAssociation> acks = new HashMap<>();
            map.entrySet().stream().forEach(entry -> {
                if (entry.getKey() <= deliveryTag) {
                    acks.put(entry.getKey(), entry.getValue());
                }
            });
            remove(acks.keySet());
            return acks.values();
        } else {
            MessageConsumerAssociation association = remove(deliveryTag);
            if (association != null) {
                return Collections.singleton(association);
            }
        }
        return Collections.emptySet();
    }

    public Collection<MessageConsumerAssociation> acknowledgeAll() {
        Set<MessageConsumerAssociation> associations = new HashSet<>();
        associations.addAll(map.values());
        map.clear();
        return associations;
    }

    public void add(long deliveryTag, String messageId, AmqpConsumer consumer, int size) {
        checkNotNull(messageId);
        checkNotNull(consumer);
        map.put(deliveryTag, new MessageConsumerAssociation(messageId, consumer, size));
    }

    public void remove(Collection<Long> deliveryTag) {
        deliveryTag.stream().forEach(tag -> {
            map.remove(tag);
        });
    }

    public MessageConsumerAssociation remove(long deliveryTag) {
        MessageConsumerAssociation entry = map.remove(deliveryTag);
        return entry;
    }

    public int size() {
        return map.size();
    }

    @VisibleForTesting
    public Map<Long, MessageConsumerAssociation> getMap() {
        return map;
    }
}
