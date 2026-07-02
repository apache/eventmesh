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

package org.apache.eventmesh.common.protocol.pipeline;

import java.util.HashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;

/**
 * Unified pipeline result with explicit action semantics.
 * Replaces the ambiguous {@code null} return value with a clear
 * {@link Action} enum that every pipeline stage mutates.
 */
public class PipelineResult {

    public enum Action {
        /** Continue to next stage normally */
        CONTINUE,
        /** Silently drop the event */
        DROP,
        /** Retry the event (with retry count in metadata) */
        RETRY,
        /** Route to dead-letter queue */
        DLQ,
        /** Fatal failure — raise alert */
        FAIL
    }

    private Action action;
    private CloudEvent event;
    private Throwable cause;
    private final Map<String, String> metadata;

    private PipelineResult(Action action, CloudEvent event, Throwable cause) {
        this.action = action;
        this.event = event;
        this.cause = cause;
        this.metadata = new HashMap<>();
    }

    // ---- Factory methods ----

    public static PipelineResult cont(CloudEvent event) {
        return new PipelineResult(Action.CONTINUE, event, null);
    }

    public static PipelineResult drop(CloudEvent event) {
        return new PipelineResult(Action.DROP, event, null);
    }

    public static PipelineResult retry(CloudEvent event, int retryCount) {
        PipelineResult r = new PipelineResult(Action.RETRY, event, null);
        r.metadata.put("retryCount", String.valueOf(retryCount));
        return r;
    }

    public static PipelineResult dlq(CloudEvent event, Throwable cause) {
        return new PipelineResult(Action.DLQ, event, cause);
    }

    public static PipelineResult fail(CloudEvent event, Throwable cause) {
        return new PipelineResult(Action.FAIL, event, cause);
    }

    // ---- Accessors ----

    public Action getAction() { return action; }
    public void setAction(Action a) { this.action = a; }

    public CloudEvent getEvent() { return event; }
    public void setEvent(CloudEvent e) { this.event = e; }

    public Throwable getCause() { return cause; }
    public void setCause(Throwable c) { this.cause = c; }

    public Map<String, String> getMetadata() { return metadata; }

    public void addMeta(String key, String value) {
        this.metadata.put(key, value);
    }

    public String getMeta(String key) {
        return metadata.get(key);
    }

    /** Convenience: was this stage passed? */
    public boolean passed() {
        return action == Action.CONTINUE;
    }

    @Override
    public String toString() {
        return "PipelineResult{action=" + action + ", event="
            + (event != null ? event.getId() : "null") + ", cause=" + cause + '}';
    }
}
