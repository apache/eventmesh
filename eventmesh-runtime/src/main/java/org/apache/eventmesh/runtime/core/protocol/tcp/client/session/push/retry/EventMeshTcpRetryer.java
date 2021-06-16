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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.retry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.openmessaging.api.Message;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshThreadFactoryImpl;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshTcpRetryer {

    public static Logger logger = LoggerFactory.getLogger(EventMeshTcpRetryer.class);

    private EventMeshTCPServer eventMeshTCPServer;

    private DelayQueue<DownStreamMsgContext> retrys = new DelayQueue<DownStreamMsgContext>();

    private ThreadPoolExecutor pool = new ThreadPoolExecutor(3,
            3,
            60000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1000),
            new EventMeshThreadFactoryImpl("eventMesh-tcp-retry", true),
            new ThreadPoolExecutor.AbortPolicy());

    private Thread dispatcher;

    public EventMeshTcpRetryer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public void pushRetry(DownStreamMsgContext downStreamMsgContext) {
        if (retrys.size() >= eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryQueueSize) {
            logger.error("pushRetry fail,retrys is too much,allow max retryQueueSize:{}, retryTimes:{}, seq:{}, bizSeq:{}",
                    eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryQueueSize, downStreamMsgContext.retryTimes,
                    downStreamMsgContext.seq, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            return;
        }

        int maxRetryTimes = EventMeshUtil.isService(downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION)) ? 1 : eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryTimes;
        if (downStreamMsgContext.retryTimes >= maxRetryTimes) {
            logger.warn("pushRetry fail,retry over maxRetryTimes:{}, retryTimes:{}, seq:{}, bizSeq:{}", maxRetryTimes, downStreamMsgContext.retryTimes,
                    downStreamMsgContext.seq, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            return;
        }

        retrys.offer(downStreamMsgContext);
        logger.info("pushRetry success,seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
    }

    public void init() {
        dispatcher = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DownStreamMsgContext downStreamMsgContext = null;
                    while ((downStreamMsgContext = retrys.take()) != null) {
                        final DownStreamMsgContext finalDownStreamMsgContext = downStreamMsgContext;
                        pool.execute(() -> {
                            retryHandle(finalDownStreamMsgContext);
                        });
                    }
                } catch (Exception e) {
                    logger.error("retry-dispatcher error!", e);
                }
            }
        }, "retry-dispatcher");
        dispatcher.setDaemon(true);
        logger.info("EventMeshTcpRetryer inited......");
    }

    private void retryHandle(DownStreamMsgContext downStreamMsgContext) {
        try {
            logger.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));

            if (isRetryMsgTimeout(downStreamMsgContext)) {
                return;
            }
            downStreamMsgContext.retryTimes++;
            downStreamMsgContext.lastPushTime = System.currentTimeMillis();

            Session rechoosen = null;
            String topic = downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
            if (!EventMeshUtil.isBroadcast(topic)) {
                rechoosen = downStreamMsgContext.session.getClientGroupWrapper()
                        .get().getDownstreamDispatchStrategy().select(downStreamMsgContext.session.getClientGroupWrapper().get().getSysId()
                                , topic
                                , downStreamMsgContext.session.getClientGroupWrapper().get().getGroupConsumerSessions());
            } else {
                rechoosen = downStreamMsgContext.session;
            }

            if (rechoosen == null) {
                logger.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));

//                //需要手动ack掉没有下发成功的消息
//                eventMeshAckMsg(downStreamMsgContext);

//                //重试找不到下发session不再回发broker或者重试其它eventMesh
//                String bizSeqNo = finalDownStreamMsgContext.msgExt.getKeys();
//                String uniqueId = MapUtils.getString(finalDownStreamMsgContext.msgExt.getProperties(), WeMQConstant.RMB_UNIQ_ID, "");
//                if(EventMeshTCPServer.getAccessConfiguration().eventMeshTcpSendBackEnabled){
//                    sendMsgBackToBroker(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }else{
//                    //TODO 将消息推给其它eventMesh，待定
//                    sendMsgToOtherEventMesh(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }
            } else {
                downStreamMsgContext.session = rechoosen;
                rechoosen.downstreamMsg(downStreamMsgContext);
                logger.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            }
        } catch (Exception e) {
            logger.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext) {
        boolean flag = false;
        String ttlStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.PROPERTY_MESSAGE_TTL);
        long ttl = StringUtils.isNumeric(ttlStr)? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;;

        String storeTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.STORE_TIME);
        long storeTimestamp = StringUtils.isNumeric(storeTimeStr)? Long.parseLong(storeTimeStr) : 0;
        String leaveTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr) : 0;
        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            logger.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            flag = true;
            eventMeshAckMsg(downStreamMsgContext);
        }
        return flag;
    }

    public void start() throws Exception {
        dispatcher.start();
        logger.info("EventMeshTcpRetryer started......");
    }

    public void shutdown() {
        pool.shutdown();
        logger.info("EventMeshTcpRetryer shutdown......");
    }

    public int getRetrySize() {
        return retrys.size();
    }

    /**
     * eventMesh ack msg
     *
     * @param downStreamMsgContext
     */
    private void eventMeshAckMsg(DownStreamMsgContext downStreamMsgContext) {
        List<Message> msgExts = new ArrayList<Message>();
        msgExts.add(downStreamMsgContext.msgExt);
        logger.warn("eventMeshAckMsg topic:{}, seq:{}, bizSeq:{}", downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION),
                downStreamMsgContext.seq, downStreamMsgContext.msgExt.getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
//        ConsumeMessageService consumeMessageService = downStreamMsgContext.consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
//        ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

    public void printRetryThreadPoolState() {
//        ThreadPoolHelper.printState(pool);
    }
}
