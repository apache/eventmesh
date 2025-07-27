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

package client.common;

/**
 * Created by v_wbyzhong on 2018/9/28.
 */
public interface ClientConstants {

    /**
     * CLIENT端心跳间隔事件
     */
    int HEATBEAT = 1000 * 60;

    long DEFAULT_TIMEOUT_IN_MILLISECONDS = 3000;

    String SYNC_TOPIC = "FT0-s-80000000-01-0";
    String ASYNC_TOPIC = "FT0-e-80010000-01-1";
    String BROADCAST_TOPIC = "FT0-e-80030000-01-3";
}
