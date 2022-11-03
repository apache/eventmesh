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

package org.apache.eventmesh.connector.qmq.utils;

import org.apache.eventmesh.api.SendResult;

import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import io.cloudevents.CloudEvent;

import java.time.ZoneId;

import qunar.tc.qmq.Message;

public class CloudEventUtils {
    public static SendResult convertSendResult(CloudEvent cloudEvent) {
        SendResult sendResult = new SendResult();
        sendResult.setTopic(cloudEvent.getSubject());
        sendResult.setMessageId(cloudEvent.getId());
        return sendResult;
    }

    //
    ///**
    // * "yyyy-MM-dd HH:mm:ss"
    // *
    // * @param dateTime
    // * @return
    // */
    //public static OffsetDateTime convertDateTime(String dateTime) {
    //    if (StringUtils.isBlank(dateTime)) {
    //        return null;
    //    }
    //    LocalDateTime localDateTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    //
    //    return OffsetDateTime.of(localDateTime, ZoneId.systemDefault().getRules().getOffset(localDateTime));
    //}
    //
    //public static String convertOffsetDateTime(OffsetDateTime offsetDateTime) {
    //
    //    if (offsetDateTime == null) {
    //        return null;
    //    }
    //    return offsetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    //}
    //
    //public static CloudEvent convertMessage2CloudEvent(Message message) {
    //    return null;
    //}
    //
    //public static Message convertCloudEvent2Message(CloudEvent event) {
    //    return null;
    //}

}
