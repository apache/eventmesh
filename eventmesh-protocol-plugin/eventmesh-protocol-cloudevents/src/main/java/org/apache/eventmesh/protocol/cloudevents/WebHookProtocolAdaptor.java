package org.apache.eventmesh.protocol.cloudevents;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.WebhookProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class WebHookProtocolAdaptor implements ProtocolAdaptor<WebhookProtocolTransportObject>{

	@Override
	public CloudEvent toCloudEvent(WebhookProtocolTransportObject protocol) throws ProtocolHandleException {
		return CloudEventBuilder.v1()
		        .withId(protocol.getCloudEventId())
		        .withSubject(protocol.getCloudEventName())
		        .withSource(URI.create(protocol.getCloudEventSource()))
		        .withDataContentType(protocol.getDataContentType())
		        .withType(protocol.getEventType())
		        .withData(protocol.getBody())
		        .build();
	}

	@Override
	public List<CloudEvent> toBatchCloudEvent(WebhookProtocolTransportObject protocol) throws ProtocolHandleException {
		List<CloudEvent> cloudEventList = new ArrayList<CloudEvent>();
		return cloudEventList;
	}

	@Override
	public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
		return null;
	}

	@Override
	public String getProtocolType() {
		return "webhookProtocolAdaptor";
	}

}
