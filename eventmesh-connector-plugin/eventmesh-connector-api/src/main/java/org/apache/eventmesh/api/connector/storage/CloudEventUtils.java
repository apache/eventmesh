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

package org.apache.eventmesh.api.connector.storage;

import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.impl.BaseCloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;

public class CloudEventUtils {
	
	public static EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat("application/cloudevents+json");

    private static Field CLOUD_EVENT_EXTENSIONS_FIELD;

    static {
        try {
            CLOUD_EVENT_EXTENSIONS_FIELD = BaseCloudEvent.class.getDeclaredField("extensions");
            CLOUD_EVENT_EXTENSIONS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e.getMessage() , e );
        }
    }

    @SuppressWarnings("unchecked")
    public static CloudEvent setValue(CloudEvent cloudEvent, String key, Object value) {
        if (Objects.nonNull(CLOUD_EVENT_EXTENSIONS_FIELD)
            && (cloudEvent instanceof CloudEventV1 || cloudEvent instanceof CloudEventV03)) {
            try {
                Map<String, Object> extensions = (Map<String, Object>) CLOUD_EVENT_EXTENSIONS_FIELD.get(cloudEvent);
                extensions.put(key, value);
                return cloudEvent;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
    
	public static String getTableName(CloudEvent cloudEvent) {
		return cloudEvent.getSubject();
	}
	
	public static String checkConsumerGroupName(String topic) {
		return topic.replace("-", "_");
	}

	public static List<Object> getParameterToCloudEvent(CloudEvent cloudEvent) {
		List<Object> parameterList = new ArrayList<>();
		String id = (String) cloudEvent.getExtension("cloudeventid");
		parameterList.add(Objects.isNull(id)?"1":id);// id
		parameterList.add(getTableName(cloudEvent));// topic
		parameterList.add("");// cloud_event_storage_node_adress
		parameterList.add("");// cloud_event_type
		parameterList.add("");// cloud_event_producer_group_name
		parameterList.add("");// cloud_event_source
		parameterList.add("application/cloudevents+json");// cloud_event_content_type
		// parameterList.add("");//cloud_event_tag
		parameterList.add("");// cloud_event_extensions
		String data = new String(CloudEventUtils.eventFormat.serialize(cloudEvent), Charset.forName("UTF-8"));
		parameterList.add(data);// cloud_event_data
		parameterList.add("{}");
		return parameterList;
	}

    public static String getNodeAdress(CloudEvent cloudEvent) {
        return (String) cloudEvent.getExtension(Constant.STORAGE_CONFIG_ADDRESS);
    }

    public static String getTopic(CloudEvent cloudEvent) {
        return cloudEvent.getSubject();
    }

    public static String getId(CloudEvent cloudEvent) {
        return "";
    }

    public static CloudEvent createCloudEvent(CloudEventInfo cloudEventInfo) {

        return null;
    }

    public static CloudEvent createReplyDataEvent(CloudEventInfo cloudEventInfo) {

        return null;
    }

    public static String getCloudEventMessageId(CloudEventInfo cloudEventInfo) {

        return null;
    }

    public static String serializeReplyData(CloudEvent cloudEvent) {
        return null;
    }

    public static String deserializeReplyData(CloudEventInfo cloudEventInfo) {
        return null;
    }

}
