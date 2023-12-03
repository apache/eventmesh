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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
public class ImServiceWrapper {

    private static final ConcurrentHashMap<ConnectRecord, CreateMessageReq> UN_ACK_REQ = new ConcurrentHashMap<>();

    private SinkConnectorConfig sinkConnectorConfig;

    private ImService imService;

    private RequestOptions requestOptions;

    private Retryer<ConnectRecord> retryer;

    public ImServiceWrapper() {}

    public static ImServiceWrapper createImServiceWrapper(SinkConnectorConfig sinkConnectorConfig) {
        ImServiceWrapper imServiceWrapper = new ImServiceWrapper();
        imServiceWrapper.sinkConnectorConfig = sinkConnectorConfig;
        imServiceWrapper.imService = Client.newBuilder(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret())
                .httpTransport(new OkHttpTransport(new OkHttpClient().newBuilder()
                        .callTimeout(3L, TimeUnit.SECONDS)
                        .build())
                )
                .disableTokenCache()
                .requestTimeout(3, TimeUnit.SECONDS)
                .build()
                .im();

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Lists.newArrayList("application/json; charset=utf-8"));

        imServiceWrapper.requestOptions = RequestOptions.newBuilder()
                .tenantAccessToken(getTenantAccessToken(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret()))
                .headers(headers)
                .build();

        long fixedWait = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
        int maxRetryTimes = Integer.parseInt(sinkConnectorConfig.getMaxRetryTimes()) + 1;
        imServiceWrapper.retryer = RetryerBuilder.<ConnectRecord>newBuilder()
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
                            log.warn("Retry sink event to lark | times=[{}]", attempt.getAttemptNumber() - 1);
                        }
                        if (times == maxRetryTimes) {
                            UN_ACK_REQ.remove(attempt.get());
                        }
                    }
                })
                .build();
        return imServiceWrapper;
    }

    public void sink(ConnectRecord connectRecord) throws ExecutionException, RetryException {
        retryer.call(() -> {
            CreateMessageReq createMessageReq = UN_ACK_REQ.computeIfAbsent(connectRecord, (k) -> {
                CreateMessageReqBody.Builder bodyBuilder = CreateMessageReqBody.newBuilder()
                        .receiveId(sinkConnectorConfig.getReceiveId())
                        .uuid(UUID.randomUUID().toString());

                String templateTypeKey = connectRecord.getExtension(ConnectRecordExtensionKeys.LARK_TEMPLATE_TYPE);
                if (null == templateTypeKey || "null".equals(templateTypeKey)) {
                    templateTypeKey = LarkMessageTemplateType.PLAIN_TEXT.getTemplateKey();
                }
                LarkMessageTemplateType templateType = LarkMessageTemplateType.of(templateTypeKey);
                if (LarkMessageTemplateType.PLAIN_TEXT == templateType) {
                    bodyBuilder.content(createTextContent(connectRecord))
                            .msgType(MsgTypeEnum.MSG_TYPE_TEXT.getValue());
                } else if (LarkMessageTemplateType.MARKDOWN == templateType) {
                    String title = Optional.ofNullable(connectRecord.getExtension(ConnectRecordExtensionKeys.LARK_MARKDOWN_MESSAGE_TITLE))
                            .orElse("EventMesh-Message");
                    bodyBuilder.content(createInteractiveContent(connectRecord, title))
                            .msgType(MsgTypeEnum.MSG_TYPE_INTERACTIVE.getValue());
                }

                return CreateMessageReq.newBuilder()
                        .receiveIdType(sinkConnectorConfig.getReceiveIdType())
                        .createMessageReqBody(bodyBuilder.build())
                        .build();
            });
            CreateMessageResp resp = imService.message().create(createMessageReq, requestOptions);
            if (resp.getCode() != 0) {
                log.warn("Sinking event to lark failure | code:[{}] | msg:[{}] | err:[{}]", resp.getCode(), resp.getMsg(), resp.getError());
                return connectRecord;
            }

            UN_ACK_REQ.remove(connectRecord);
            return null;
        });
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

        // cardUrl, Only in need of PC, mobile side jump different links to use
        /*MessageCardURL cardURL = MessageCardURL.newBuilder()
                .pcUrl("http://www.baidu.com")
                .iosUrl("http://www.google.com")
                .url("http://open.feishu.com")
                .androidUrl("http://www.jianshu.com")
                .build();*/

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
        String atAll = connectRecord.getExtension(ConnectRecordExtensionKeys.LARK_AT_ALL);
        return null != atAll && !"null".equals(atAll) && Boolean.parseBoolean(atAll);
    }

    private String needAtUser(ConnectRecord connectRecord) {
        String atUsers = connectRecord.getExtension(ConnectRecordExtensionKeys.LARK_AT_USERS);
        return null != atUsers && !"null".equals(atUsers) ? atUsers : "";
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