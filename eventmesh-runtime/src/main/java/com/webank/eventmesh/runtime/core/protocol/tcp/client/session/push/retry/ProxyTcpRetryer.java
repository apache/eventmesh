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

package com.webank.eventmesh.runtime.core.protocol.tcp.client.session.push.retry;

import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.runtime.util.ProxyThreadFactoryImpl;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.boot.ProxyTCPServer;
import com.webank.eventmesh.runtime.constants.DeFiBusConstant;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
//import com.webank.eventmesh.connector.defibus.common.Constants;
import io.openmessaging.api.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProxyTcpRetryer {

    public static Logger logger = LoggerFactory.getLogger(ProxyTcpRetryer.class);

    private ProxyTCPServer proxyTCPServer;

    private DelayQueue<DownStreamMsgContext> retrys = new DelayQueue<DownStreamMsgContext>();

    private ThreadPoolExecutor pool = new ThreadPoolExecutor(3,
            3,
            60000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1000),
            new ProxyThreadFactoryImpl("proxy-tcp-retry",true),
            new ThreadPoolExecutor.AbortPolicy());

    private Thread dispatcher;

    public ProxyTcpRetryer(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }

    public ProxyTCPServer getProxyTCPServer() {
        return proxyTCPServer;
    }

    public void setProxyTCPServer(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }

    public void pushRetry(DownStreamMsgContext downStreamMsgContext) {
        if (retrys.size() >= proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryQueueSize) {
            logger.error("pushRetry fail,retrys is too much,allow max retryQueueSize:{}, retryTimes:{}, seq:{}, bizSeq:{}",
                    proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryQueueSize, downStreamMsgContext.retryTimes,
                    downStreamMsgContext.seq, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            return;
        }

        int maxRetryTimes = ProxyUtil.isService(downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION)) ? 1 : proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryTimes;
        if (downStreamMsgContext.retryTimes >= maxRetryTimes) {
            logger.warn("pushRetry fail,retry over maxRetryTimes:{}, retryTimes:{}, seq:{}, bizSeq:{}", maxRetryTimes, downStreamMsgContext.retryTimes,
                    downStreamMsgContext.seq, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            return;
        }

        retrys.offer(downStreamMsgContext);
        logger.info("pushRetry success,seq:{}, retryTimes:{}, bizSeq:{}",downStreamMsgContext.seq, downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
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
        logger.info("ProxyTcpRetryer inited......");
    }

    private void retryHandle(DownStreamMsgContext downStreamMsgContext){
        try {
            logger.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}",downStreamMsgContext.seq, downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));

            if(isRetryMsgTimeout(downStreamMsgContext)){
                return;
            }
            downStreamMsgContext.retryTimes++;
            downStreamMsgContext.lastPushTime = System.currentTimeMillis();

            Session rechoosen = null;
            String topic = downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
            if(!ProxyUtil.isBroadcast(topic)){
                rechoosen = downStreamMsgContext.session.getClientGroupWrapper()
                        .get().getDownstreamDispatchStrategy().select(downStreamMsgContext.session.getClientGroupWrapper().get().getGroupName()
                                , topic
                                , downStreamMsgContext.session.getClientGroupWrapper().get().getGroupConsumerSessions());
            }else{
                rechoosen = downStreamMsgContext.session;
            }


            if(rechoosen == null){
                logger.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));

//                //需要手动ack掉没有下发成功的消息
//                proxyAckMsg(downStreamMsgContext);

//                //重试找不到下发session不再回发broker或者重试其它proxy
//                String bizSeqNo = finalDownStreamMsgContext.msgExt.getKeys();
//                String uniqueId = MapUtils.getString(finalDownStreamMsgContext.msgExt.getProperties(), WeMQConstant.RMB_UNIQ_ID, "");
//                if(weMQProxyTCPServer.getAccessConfiguration().proxyTcpSendBackEnabled){
//                    sendMsgBackToBroker(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }else{
//                    //TODO 将消息推给其它proxy，待定
//                    sendMsgToOtherProxy(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }
            }else {
                downStreamMsgContext.session = rechoosen;

                if (rechoosen.isCanDownStream()) {
                    rechoosen.downstreamMsg(downStreamMsgContext);
                    logger.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}",downStreamMsgContext.seq, downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
                }else{
                    logger.warn("session is busy,push retry again,seq:{}, session:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.session.getClient(), ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
                    long delayTime = ProxyUtil.isService(topic) ? 0 : proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryDelayInMills;
                    downStreamMsgContext.delay(delayTime);
                    pushRetry(downStreamMsgContext);
                }
            }
        } catch (Exception e) {
            logger.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext){
        boolean flag =false;
        long ttl = Long.parseLong(downStreamMsgContext.msgExt.getUserProperties(ProxyConstants.PROPERTY_MESSAGE_TTL));
        //TODO 关注是否能取到
        long storeTimestamp = Long.parseLong(downStreamMsgContext.msgExt.getUserProperties(DeFiBusConstant.STORE_TIME));
        String leaveTimeStr = downStreamMsgContext.msgExt.getUserProperties(DeFiBusConstant.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = downStreamMsgContext.msgExt.getUserProperties(DeFiBusConstant.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr) : 0;
        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            logger.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
            flag = true;
            proxyAckMsg(downStreamMsgContext);
        }
        return flag;
    }

    public void start() throws Exception {
        dispatcher.start();
        logger.info("ProxyTcpRetryer started......");
    }

    public void shutdown() {
        pool.shutdown();
        logger.info("ProxyTcpRetryer shutdown......");
    }

    public int getRetrySize(){
        return retrys.size();
    }

    /**
     * proxy ack msg
     *
     * @param downStreamMsgContext
     */
    private void proxyAckMsg(DownStreamMsgContext downStreamMsgContext){
        List<Message> msgExts = new ArrayList<Message>();
        msgExts.add(downStreamMsgContext.msgExt);
        logger.warn("proxyAckMsg topic:{}, seq:{}, bizSeq:{}",downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION),
                downStreamMsgContext.seq, downStreamMsgContext.msgExt.getSystemProperties(ProxyConstants.PROPERTY_MESSAGE_KEYS));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
//        ConsumeMessageService consumeMessageService = downStreamMsgContext.consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
//        ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

    public void printRetryThreadPoolState(){
//        ThreadPoolHelper.printState(pool);
    }
}
