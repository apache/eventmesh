package org.apache.eventmesh.connector.pravega.client;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;

import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeTask extends Thread {
    private final EventStreamReader<byte[]> reader;
    private final EventListener listener;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SubscribeTask(String name, EventStreamReader<byte[]> reader, EventListener listener) {
        super(name);
        this.reader = reader;
        this.listener = listener;
    }

    @Override
    public void run() {
        while (running.get()) {
            EventRead<byte[]> event;
            while ((event = reader.readNextEvent(2000)) != null) {
                byte[] eventByteArray = event.getEvent();
                if (eventByteArray == null) {
                    continue;
                }
                PravegaEvent pravegaEvent = PravegaEvent.getFromByteArray(eventByteArray);
                CloudEvent cloudEvent = pravegaEvent.convertToCloudEvent();
                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                    @Override
                    public void commit(EventMeshAction action) {
                        // nothing to do
                    }
                };
                listener.consume(cloudEvent, consumeContext);
            }
        }
    }

    public void stopRead() {
        running.compareAndSet(true, false);
    }
}
