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

package cn.webank.defibus.namesrv;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.namesrv.NamesrvStartup;

public class DeFiBusNamesrvStartup {
    private static final String DEFAULT_ROCKETMQ_HOME_PATH = ".";

    //init default rocketmq home path
    public static void initRocketMQHomePath() {
        String rocketmqHome = System.getProperty(MixAll.ROCKETMQ_HOME_PROPERTY, System.getenv(MixAll.ROCKETMQ_HOME_ENV));
        if (StringUtils.isBlank(rocketmqHome)) {
            System.setProperty(MixAll.ROCKETMQ_HOME_PROPERTY, DEFAULT_ROCKETMQ_HOME_PATH);
        }
    }

    public static void main(String[] args) {
        initRocketMQHomePath();
        NamesrvStartup.main0(args);
    }
}
