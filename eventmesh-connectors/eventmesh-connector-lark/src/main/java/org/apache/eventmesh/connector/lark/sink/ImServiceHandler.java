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

package org.apache.eventmesh.connector.lark.sink;

import static org.apache.eventmesh.connector.lark.sink.connector.LarkSinkConnector.getTenantAccessToken;

import org.apache.eventmesh.connector.lark.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.lark.config.LarkMessageTemplateType;
import org.apache.eventmesh.connector.lark.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.text.StringEscapeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.lark.oapi.Client;
import com.lark.oapi.card.enums.MessageCardHeaderTemplateEnum;
import com.lark.oapi.card.model.MessageCard;
import com.lark.oapi.card.model.MessageCardConfig;
import com.lark.oapi.card.model.MessageCardElement;
import com.lark.oapi.card.model.MessageCardHeader;
import com.lark.oapi.card.model.MessageCardMarkdown;
import com.lark.oapi.card.model.MessageCardPlainText;
import com.lark.oapi.core.httpclient.OkHttpTransport;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.core.utils.Lists;
import com.lark.oapi.okhttp.OkHttpClient;
import com.lark.oapi.service.im.v1.ImService;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ext.MessageText;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImServiceHandler {

    private SinkConnectorConfig sinkConnectorConfig;

    private ImService imService;

    private Retryer<ConnectRecord> retryer;

    private ExecutorService sinkAsyncWorker;
    private ExecutorService cleanerWorker;
    private ScheduledExecutorService retryWorker;

    private static final LongAdder redoSinkNum = new LongAdder();

    public ImServiceHandler() {
    }

    public static ImServiceHandler create(SinkConnectorConfig sinkConnectorConfig) {
        ImServiceHandler imServiceHandler = new ImServiceHandler();
        imServiceHandler.sinkConnectorConfig = sinkConnectorConfig;
        imServiceHandler.imService = Client.newBuilder(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret())
            .httpTransport(new OkHttpTransport(new OkHttpClient().newBuilder()
                .callTimeout(3L, TimeUnit.SECONDS)
                .build()))
            .disableTokenCache()
            .requestTimeout(3, TimeUnit.SECONDS)
            .build()
            .im();

        long fixedWait = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
        int maxRetryTimes = Integer.parseInt(sinkConnectorConfig.getMaxRetryTimes()) + 1;
        if (Boolean.parseBoolean(sinkConnectorConfig.getSinkAsync())) {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            imServiceHandler.sinkAsyncWorker = Executors.newFixedThreadPool(availableProcessors, r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("eventmesh-connector-lark-sinkAsyncWorker");
                return thread;
            });

            imServiceHandler.cleanerWorker = Executors.newFixedThreadPool(availableProcessors, r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("eventmesh-connector-lark-cleanerWorker");
                return thread;
            });

            imServiceHandler.retryWorker = Executors.newScheduledThreadPool(availableProcessors, r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("eventmesh-connector-lark-retryWorker");
                return thread;
            });
        } else {
            imServiceHandler.retryer = RetryerBuilder.<ConnectRecord>newBuilder()
                .retryIfException()
                .retryIfResult(Objects::nonNull)
                .withWaitStrategy(WaitStrategies.fixedWait(fixedWait, TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetryTimes))
                .withRetryListener(new RetryListener() {

                    @SneakyThrows
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {

                        long times = attempt.getAttemptNumber();
                        if (times > 1) {
                            redoSinkNum.increment();
                            log.info("Total redo sink task num : [{}]", redoSinkNum.sum());
                            log.warn("Retry sink event to lark | times=[{}]", attempt.getAttemptNumber() - 1);
                        }
                    }
                })
                .build();
        }

        return imServiceHandler;
    }

    public void sink(ConnectRecord connectRecord) throws ExecutionException, RetryException {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Lists.newArrayList("application/json; charset=utf-8"));

        RequestOptions requestOptions = RequestOptions.newBuilder()
            .tenantAccessToken(getTenantAccessToken(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret()))
            .headers(headers)
            .build();

        retryer.call(() -> {
            CreateMessageReq createMessageReq = convertCreateMessageReq(connectRecord);
            CreateMessageResp resp = imService.message().create(createMessageReq, requestOptions);
            if (resp.getCode() != 0) {
                log.warn("Sinking event to lark failure | code:[{}] | msg:[{}] | err:[{}]", resp.getCode(), resp.getMsg(), resp.getError());
                return connectRecord;
            }
            return null;
        });
    }

    public void sinkAsync(ConnectRecord connectRecord) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Lists.newArrayList("application/json; charset=utf-8"));

        RequestOptions requestOptions = RequestOptions.newBuilder()
            .tenantAccessToken(getTenantAccessToken(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret()))
            .headers(headers)
            .build();

        CreateMessageReq createMessageReq = convertCreateMessageReq(connectRecord);

        long fixedWait = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
        int maxRetryTimes = Integer.parseInt(sinkConnectorConfig.getMaxRetryTimes()) + 1;
        LongAdder cnt = new LongAdder();
        AtomicBoolean isAck = new AtomicBoolean(false);
        Runnable task = () -> CompletableFuture
            .supplyAsync(() -> {
                try {
                    cnt.increment();
                    return imService.message().create(createMessageReq, requestOptions);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, sinkAsyncWorker)
            .whenCompleteAsync((resp, e) -> {
                if (cnt.sum() > 1) {
                    redoSinkNum.increment();
                    log.info("Total redo sink task num : [{}]", redoSinkNum.sum());
                    log.warn("Retry sink event to lark | times=[{}]", cnt.sum() - 1);
                }
                if (Objects.nonNull(e)) {
                    log.error("eventmesh-connector-lark internal exception.", e);
                    return;
                }
                if (resp.getCode() != 0) {
                    log.warn("Sinking event to lark failure | code:[{}] | msg:[{}] | err:[{}]", resp.getCode(), resp.getMsg(), resp.getError());
                    return;
                }
                isAck.set(true);
            });

        ScheduledFuture<?> future = retryWorker.scheduleAtFixedRate(task, 0L, fixedWait, TimeUnit.MILLISECONDS);
        cleanerWorker.submit(() -> {
            while (true) {
                // complete task
                if (isAck.get() || cnt.sum() >= maxRetryTimes) {
                    future.cancel(true);
                    return;
                }
            }
        });
    }

    private CreateMessageReq convertCreateMessageReq(ConnectRecord connectRecord) {
        CreateMessageReqBody.Builder bodyBuilder = CreateMessageReqBody.newBuilder()
            .receiveId(sinkConnectorConfig.getReceiveId())
            .uuid(UUID.randomUUID().toString());

        String templateTypeKey = connectRecord.getExtension(ConnectRecordExtensionKeys.TEMPLATE_TYPE_4_LARK);
        if (templateTypeKey == null || "null".equals(templateTypeKey)) {
            templateTypeKey = LarkMessageTemplateType.PLAIN_TEXT.getTemplateKey();
        }
        LarkMessageTemplateType templateType = LarkMessageTemplateType.of(templateTypeKey);
        if (LarkMessageTemplateType.PLAIN_TEXT == templateType) {
            bodyBuilder.content(createTextContent(connectRecord))
                .msgType(MsgTypeEnum.MSG_TYPE_TEXT.getValue());
        } else if (LarkMessageTemplateType.MARKDOWN == templateType) {
            String title = Optional.ofNullable(connectRecord.getExtension(ConnectRecordExtensionKeys.MARKDOWN_MESSAGE_TITLE_4_LARK))
                .orElse("EventMesh-Message");
            bodyBuilder.content(createInteractiveContent(connectRecord, title))
                .msgType(MsgTypeEnum.MSG_TYPE_INTERACTIVE.getValue());
        }

        return CreateMessageReq.newBuilder()
            .receiveIdType(sinkConnectorConfig.getReceiveIdType())
            .createMessageReqBody(bodyBuilder.build())
            .build();
    }

    private String createTextContent(ConnectRecord connectRecord) {
        MessageText.Builder msgBuilder = MessageText.newBuilder();

        if (needAtAll(connectRecord)) {
            msgBuilder.atAll();
        }
        String atUsers = needAtUser(connectRecord);
        if (!atUsers.isEmpty()) {
            String[] users = atUsers.split(";");

            for (String user : users) {
                String[] kv = user.split(",");
                msgBuilder.atUser(kv[0], kv[1]);
            }
        }

        String escapedString = StringEscapeUtils.escapeJava(new String((byte[]) connectRecord.getData()));
        return msgBuilder.text(escapedString).build();
    }

    private String createInteractiveContent(ConnectRecord connectRecord, String title) {
        StringBuilder sb = new StringBuilder();
        if (needAtAll(connectRecord)) {
            atAll(sb);
        }
        String atUsers = needAtUser(connectRecord);
        if (!atUsers.isEmpty()) {
            String[] users = atUsers.split(";");

            for (String user : users) {
                String[] kv = user.split(",");
                atUser(sb, kv[0]);
            }
        }
        sb.append(new String((byte[]) connectRecord.getData()));

        MessageCardConfig config = MessageCardConfig.newBuilder()
            .enableForward(true)
            .wideScreenMode(true)
            .updateMulti(true)
            .build();

        // header
        MessageCardHeader header = MessageCardHeader.newBuilder()
            .template(MessageCardHeaderTemplateEnum.BLUE)
            .title(MessageCardPlainText.newBuilder()
                .content(title)
                .build())
            .build();

        MessageCard content = MessageCard.newBuilder()
            .config(config)
            .header(header)
            .elements(new MessageCardElement[]{
                    MessageCardMarkdown.newBuilder().content(sb.toString()).build()
            })
            .build();

        return content.String();
    }

    private boolean needAtAll(ConnectRecord connectRecord) {
        String atAll = connectRecord.getExtension(ConnectRecordExtensionKeys.AT_ALL_4_LARK);
        return atAll != null && !"null".equals(atAll) && Boolean.parseBoolean(atAll);
    }

    private String needAtUser(ConnectRecord connectRecord) {
        String atUsers = connectRecord.getExtension(ConnectRecordExtensionKeys.AT_USERS_4_LARK);
        return atUsers != null && !"null".equals(atUsers) ? atUsers : "";
    }

    /**
     * For markdown template type.
     *
     * @param sb StringBuilder
     */
    private void atAll(StringBuilder sb) {
        sb.append("<at id=all>")
            .append("</at>");
    }

    /**
     * For markdown template type
     *
     * @param sb     StringBuilder
     * @param userId open_id/union_id/user_id, recommend to use open_id. Custom robots can only be used open_id,
     */
    private void atUser(StringBuilder sb, String userId) {
        sb.append("<at id=")
            .append(userId)
            .append(">")
            .append("</at>");
    }
}