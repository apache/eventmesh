package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventEmitter<T> {
    private final Logger logger = LoggerFactory.getLogger(EventEmitter.class);

    private final StreamObserver<T> emitter;

    public EventEmitter(StreamObserver<T> emitter) {
        this.emitter = emitter;
    }

    public synchronized void onNext(T event) {
        try {
            emitter.onNext(event);
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onNext. {}", t.getMessage());
        }
    }

    public synchronized void onCompleted() {
        try {
            emitter.onCompleted();
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onCompleted. {}", t.getMessage());
        }
    }

    public synchronized void onError(Throwable t) {
        try {
            emitter.onError(t);
        } catch (Throwable t1) {
            logger.warn("StreamObserver Error onError. {}", t1.getMessage());
        }
    }

    public StreamObserver<T> getEmitter() {
        return emitter;
    }
}
