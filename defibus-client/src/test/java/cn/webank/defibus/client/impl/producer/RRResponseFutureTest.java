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

package cn.webank.defibus.client.impl.producer;

import org.apache.rocketmq.common.message.Message;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RRResponseFutureTest {
    @Test
    public void test() {
        RRResponseFuture responseFuture = new RRResponseFuture();
        ResponseTable.getRrResponseFurtureConcurrentHashMap().put("key1", responseFuture);

        RRResponseFuture responseFuture2 = new RRResponseFuture(new RRCallback() {
            @Override public void onSuccess(Message msg) {

            }

            @Override public void onException(Throwable e) {

            }
        });
        ResponseTable.getRrResponseFurtureConcurrentHashMap().put("key2", responseFuture2);

        RRResponseFuture responseFuture3 = new RRResponseFuture(new RRCallback() {
            @Override public void onSuccess(Message msg) {

            }

            @Override public void onException(Throwable e) {

            }
        }, 3000);
        ResponseTable.getRrResponseFurtureConcurrentHashMap().put("key3", responseFuture3);
    }

    @Test
    public void test_watiSuccess() throws InterruptedException {
        RRResponseFuture responseFuture = new RRResponseFuture(new RRCallback() {
            @Override public void onSuccess(Message msg) {

            }

            @Override public void onException(Throwable e) {

            }
        }, 3000);
        Message rspMsg = new Message();
        rspMsg.setTopic("FooBar");
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(1000);
                    responseFuture.putResponse(rspMsg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        Message rsp = responseFuture.waitResponse(3000);
        assertThat(rsp).isNotNull();
        assertThat(rsp).isEqualTo(rspMsg);
    }

    @Test
    public void test_watiTimeout() throws InterruptedException {
        RRResponseFuture responseFuture = new RRResponseFuture(new RRCallback() {
            @Override public void onSuccess(Message msg) {
            }

            @Override public void onException(Throwable e) {
            }
        }, 1000);

        Message rsp = responseFuture.waitResponse(1000);
        assertThat(rsp).isNull();
    }
}
