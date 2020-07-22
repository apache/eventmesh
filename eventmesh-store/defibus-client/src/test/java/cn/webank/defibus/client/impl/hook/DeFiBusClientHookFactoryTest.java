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

package cn.webank.defibus.client.impl.hook;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeFiBusClientHookFactoryTest {
    @Test
    public void test() {
        DeFiBusClientHookFactory.createRPCHook(null);
    }

    @Test
    public void test_doBefore_doAfter() {
        AtomicInteger invokeTimes = new AtomicInteger(0);
        RPCHook rpcHook = new RPCHook() {
            @Override public void doBeforeRequest(String remoteAddr, RemotingCommand request) {
                invokeTimes.getAndIncrement();
            }

            @Override
            public void doAfterResponse(String remoteAddr, RemotingCommand request, RemotingCommand response) {
                invokeTimes.getAndIncrement();
            }
        };
        DeFiBusClientHookFactory.createRPCHook(rpcHook);
        rpcHook.doBeforeRequest("", null);
        assertThat(invokeTimes.get()).isEqualTo(1);
        rpcHook.doAfterResponse("", null, null);
        assertThat(invokeTimes.get()).isEqualTo(2);
    }
}
