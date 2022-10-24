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

package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigurationWrapper;

public class EventMeshAmqpConfiguration extends CommonConfiguration {

    public int eventMeshAmqpServerPort = 10000;

    // default frame size of broker
    public int defaultNetworkBufferSize = 256 * 1024;

    /**
     * default min size of frame
     */
    public int minFrameSize = 4096;

    /**
     * default max size of frame
     */
    public int maxFrameSize = 4096;

    /**
     * default max number of channels
     */
    public int maxNoOfChannels = 64;

    /**
     * default heart beat
     */
    public int heartBeat = 60 * 1000;

    /**
     * default max message size
     */
    public long maxMessageSize = 1024 * 1024 * 10;

    public EventMeshAmqpConfiguration(ConfigurationWrapper configurationWrapper) {
        super(configurationWrapper);
    }
}