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

package org.apache.eventmesh.runtime.core.protocol.amqp.exchange;

/**
 * Defines the names of the standard AMQP exchanges that every AMQP broker should provide. These exchange names and type
 * are given in the specification.
 *
 * <p>
 * TODO A type safe enum, might be more appropriate for the exchange types.
 */
public class ExchangeDefaults {
    private ExchangeDefaults() {
    }

    /**
     * The default direct exchange, which is a special internal exchange that cannot be explicitly bound to.
     */
    public static final String DEFAULT_EXCHANGE_NAME = "__default_ex__";
    public static final String DEFAULT_EXCHANGE_NAME_DURABLE = "__default_ex_durable__";
    /**
     * The pre-defined topic exchange, the broker SHOULD provide this.
     */
    public static final String TOPIC_EXCHANGE_NAME = "amq.topic";

    /**
     * Defines the identifying type name of topic exchanges.
     */
    public static final String TOPIC_EXCHANGE_CLASS = "topic";

    /**
     * The pre-defined direct exchange, the broker MUST provide this.
     */
    public static final String DIRECT_EXCHANGE_NAME = "amq.direct";

    /**
     * Defines the identifying type name of direct exchanges.
     */
    public static final String DIRECT_EXCHANGE_CLASS = "direct";

    /**
     * The pre-defined headers exchange, the specification does not say this needs to be provided.
     */
    public static final String HEADERS_EXCHANGE_NAME = "amq.match";

    /**
     * Defines the identifying type name of headers exchanges.
     */
    public static final String HEADERS_EXCHANGE_CLASS = "headers";

    /**
     * The pre-defined fanout exchange, the boker MUST provide this.
     */
    public static final String FANOUT_EXCHANGE_NAME = "amq.fanout";

    /**
     * Defines the identifying type name of fanout exchanges.
     */
    public static final String FANOUT_EXCHANGE_CLASS = "fanout";


}
