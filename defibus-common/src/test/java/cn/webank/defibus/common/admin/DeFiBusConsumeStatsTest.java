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

package cn.webank.defibus.common.admin;

import java.util.HashMap;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DeFiBusConsumeStatsTest {
    private String topic = "topic";
    private String brokerName = "brokerName";
    private HashMap<MessageQueue, DeFiBusOffsetWrapper> offsetTable = new HashMap<MessageQueue, DeFiBusOffsetWrapper>();
    private DeFiBusConsumeStats deFiBusConsumeStats = new DeFiBusConsumeStats();

    @Test
    public void testComputeTotalDiff() throws Exception {
        long totalDiff = createOffsetTable(10);
        deFiBusConsumeStats.setOffsetTable(offsetTable);
        long i = deFiBusConsumeStats.computeTotalDiff();
        assertTrue(totalDiff == deFiBusConsumeStats.computeTotalDiff());
    }

    private long createOffsetTable(int queueSize) {
        long totalDiff = 0;
        for (int i = 0; i < queueSize; i++) {
            DeFiBusOffsetWrapper wrapper = new DeFiBusOffsetWrapper();
            wrapper.setBrokerOffset(queueSize);
            wrapper.setConsumerOffset(i);
            long temp = queueSize - i;
            totalDiff += temp;
            offsetTable.put(new MessageQueue(topic, brokerName, i), wrapper);

        }
        return totalDiff;
    }
}
