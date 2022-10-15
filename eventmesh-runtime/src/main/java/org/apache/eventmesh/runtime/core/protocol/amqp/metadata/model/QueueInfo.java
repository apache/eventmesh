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

package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class QueueInfo {

    private String creationDate;
    private long lastVisitTime;

    private String queueName;
    private boolean durable;
    private boolean autoDelete;
    private boolean exclusive;
    private String connectionId;
    private Map<String, String> arguments;
    private Set<String> exchanges;

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    public void setLastVisitTime(long lastVisitTime) {
        this.lastVisitTime = lastVisitTime;
    }

    public String getQueueName() {
        return queueName;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public Set<String> getExchanges() {
        Set<String> set = new HashSet<>();
        set.addAll(exchanges);
        return set;
    }

    public synchronized void addBinding(String exchange) {
        exchanges.add(exchange);
    }

    public synchronized void removeBinding(String exchange) {
        exchanges.remove(exchange);
    }

    public synchronized void removeAll() {
        exchanges.clear();
    }
}
