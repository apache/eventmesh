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

package org.apache.eventmesh.common.wire;

import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * The single internal wire format for EventMesh — one frame, two message families. Used on every
 * internal path: streaming session/subscribe (runtime↔agent over lite topics), normal pub/sub
 * (runtime↔storage MQ), and cross-instance forwarding. The PUBLIC surface (HTTP/SSE/TCP/gRPC/A2A)
 * stays CloudEvents; the runtime converts at the edge (CE → EventMeshFrame on ingress,
 * EventMeshFrame → CE on egress). See {@code docs/eventmesh-architecture-refinement.md}.
 *
 * <p>This unifies the former {@code PrivateFrame} (streaming-only, fixed sessionId/seq fields) and
 * {@code CloudEventEnvelope} (event-only, all-KV) into one format whose fixed header serves the
 * streaming hot path while a generic KV section carries arbitrary named attributes for events.
 *
 * <h3>Wire layout</h3>
 * <pre>
 *   fixed header (14 bytes, big-endian):
 *     [magic:1]    = 0xEF ('E'+'F' sentinel)
 *     [ver:1]      = 1
 *     [msgType:1]  = 1 STREAM_REQ | 2 STREAM_CHUNK | 3 EVENT
 *     [flags:1]    = bit0 done | bit1 hasError | bit2 hasMeta | bit3..7 reserved
 *     [seq:4]      = stream sequence (STREAM_CHUNK only; 0 otherwise)
 *     [keyCount:2] = number of KV attribute entries
 *     [dataLen:4]  = data byte length
 *
 *   KV attributes (keyCount × ):
 *     [nameLen:2][name]    UTF-8
 *     [valLen:4][value]    UTF-8 (toString of the source value)
 *
 *   data payload:
 *     [data: dataLen]      raw bytes (chunk text / prompt / CloudEvent data)
 * </pre>
 *
 * <p><b>STREAM_REQ</b> uses KV {@code replyTo/model/conversationId} + data=prompt.
 * <b>STREAM_CHUNK</b> uses {@code seq} (fixed) + {@code done} (flag) + KV
 * {@code sessionId/eventType/error/meta} + data=chunk text. <b>EVENT</b> uses KV
 * {@code id/type/subject/time/emttl/emcorrelationid/...} (whatever the CloudEvent carried) + data.
 * On decode, EVENT KV names that match standard CE attributes are rebuilt via the right builder
 * methods; everything else becomes an extension.</p>
 */
public final class EventMeshFrame {

    public static final int MAGIC = 0xEF;
    public static final int VERSION = 1;
    public static final int TYPE_STREAM_REQ = 1;
    public static final int TYPE_STREAM_CHUNK = 2;
    public static final int TYPE_EVENT = 3;

    // flag bits
    static final int FLAG_DONE = 0x01;
    static final int FLAG_HAS_ERROR = 0x02;
    static final int FLAG_HAS_META = 0x04;

    private static final int HEADER_LEN = 14; // magic(1)+ver(1)+msgType(1)+flags(1)+seq(4)+keyCount(2)+dataLen(4)

    // well-known KV keys (kept short to save bytes on the wire)
    static final String K_SESSION_ID = "sid";
    static final String K_REPLY_TO = "replyTo";
    static final String K_MODEL = "model";
    static final String K_CONVERSATION_ID = "conv";
    static final String K_EVENT_TYPE = "etype";
    static final String K_ERROR = "err";
    static final String K_META = "meta";

    private final int msgType;
    private final int flags;
    private final int seq;
    private final Map<String, String> attrs;
    private final byte[] data;

    EventMeshFrame(int msgType, int flags, int seq, Map<String, String> attrs, byte[] data) {
        this.msgType = msgType;
        this.flags = flags;
        this.seq = seq;
        this.attrs = attrs;
        this.data = data;
    }

    // -------------------- factories: streaming --------------------

    public static EventMeshFrame fromRequest(StreamRequest req) {
        Map<String, String> a = new LinkedHashMap<>();
        String sid = req.getSessionId() == null ? "" : req.getSessionId();
        a.put(K_SESSION_ID, sid);
        if (req.getReplyTo() != null && !req.getReplyTo().isEmpty()) {
            a.put(K_REPLY_TO, req.getReplyTo());
        }
        if (req.getModel() != null && !req.getModel().isEmpty()) {
            a.put(K_MODEL, req.getModel());
        }
        if (req.getConversationId() != null && !req.getConversationId().isEmpty()) {
            a.put(K_CONVERSATION_ID, req.getConversationId());
        }
        byte[] data = utf8(req.getPrompt() == null ? "" : req.getPrompt());
        return new EventMeshFrame(TYPE_STREAM_REQ, 0, 0, a, data);
    }

    public static EventMeshFrame fromChunk(StreamChunk c) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put(K_SESSION_ID, c.getSessionId() == null ? "" : c.getSessionId());
        int flags = c.isDone() ? FLAG_DONE : 0;
        if (c.getError() != null && !c.getError().isEmpty()) {
            flags |= FLAG_HAS_ERROR;
            a.put(K_ERROR, c.getError());
        }
        if (c.getEventType() != null && !c.getEventType().isEmpty()) {
            a.put(K_EVENT_TYPE, c.getEventType());
        }
        if (c.getMeta() != null && !c.getMeta().isEmpty()) {
            flags |= FLAG_HAS_META;
            a.put(K_META, MetaJson.stringify(c.getMeta()));
        }
        byte[] data = utf8(c.getChunk() == null ? "" : c.getChunk());
        return new EventMeshFrame(TYPE_STREAM_CHUNK, flags, c.getSeq(), a, data);
    }

    // -------------------- factories: CloudEvent (normal pub/sub) --------------------

    /** Build an EVENT frame from pre-mapped attributes + raw data (used by protocol adaptors that
     *  map a non-CloudEvents protocol directly, e.g. MeshMessage). */
    public static EventMeshFrame event(Map<String, String> attrs, byte[] data) {
        return new EventMeshFrame(TYPE_EVENT, 0, 0, attrs, data);
    }

    /** Capture an arbitrary {@link CloudEvent} as an EVENT frame (all attributes + extensions + data). */
    public static EventMeshFrame fromCloudEvent(CloudEvent event) {
        Map<String, String> a = new LinkedHashMap<>();
        for (String name : event.getAttributeNames()) {
            Object v = event.getAttribute(name);
            if (v != null) {
                a.put(name, v.toString());
            }
        }
        for (String name : event.getExtensionNames()) {
            Object v = event.getExtension(name);
            if (v != null) {
                a.put(name, v.toString());
            }
        }
        byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
        return new EventMeshFrame(TYPE_EVENT, 0, 0, a, data);
    }

    /** Reconstruct the {@link CloudEvent} (EVENT frame → attributes + extensions + data). */
    public CloudEvent toCloudEvent() {
        if (msgType != TYPE_EVENT) {
            throw new IllegalStateException("not an EVENT frame");
        }
        CloudEventBuilder b = CloudEventBuilder.v1();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            applyCe(b, e.getKey(), e.getValue());
        }
        if (data.length > 0) {
            b.withData(data);
        }
        return b.build();
    }

    private static void applyCe(CloudEventBuilder b, String name, String value) {
        // specversion is implied (v1 builder → V1); datacontentencoding was removed in CE 3.0.
        switch (name) {
            case "specversion":
                return; // builder defaults to V1.0
            case "id":
                b.withId(value);
                return;
            case "source":
                b.withSource(URI.create(value));
                return;
            case "type":
                b.withType(value);
                return;
            case "datacontenttype":
                b.withDataContentType(value);
                return;
            case "subject":
                b.withSubject(value);
                return;
            case "time":
                b.withTime(OffsetDateTime.parse(value));
                return;
            case "dataschema":
                b.withDataSchema(URI.create(value));
                return;
            case "datacontentencoding":
                return; // removed in CE 3.0; drop silently
            default:
                b.withExtension(name, value);
        }
    }

    // -------------------- decode back to streaming DTOs --------------------

    public StreamRequest toStreamRequest() {
        if (msgType != TYPE_STREAM_REQ) {
            throw new IllegalStateException("not a STREAM_REQ frame");
        }
        return StreamRequest.builder()
            .sessionId(attrs.get(K_SESSION_ID))
            .replyTo(attrs.get(K_REPLY_TO))
            .model(attrs.get(K_MODEL))
            .conversationId(attrs.get(K_CONVERSATION_ID))
            .prompt(str(data))
            .build();
    }

    public StreamChunk toChunk() {
        if (msgType != TYPE_STREAM_CHUNK) {
            throw new IllegalStateException("not a STREAM_CHUNK frame");
        }
        return StreamChunk.builder()
            .sessionId(attrs.get(K_SESSION_ID))
            .seq(seq)
            .chunk(str(data))
            .done((flags & FLAG_DONE) != 0)
            .error(attrs.get(K_ERROR))
            .eventType(attrs.get(K_EVENT_TYPE))
            .meta(attrs.containsKey(K_META) ? MetaJson.parse(attrs.get(K_META)) : null)
            .build();
    }

    // -------------------- encode / decode --------------------

    public byte[] encode() {
        byte[][] nameBytes = new byte[attrs.size()][];
        byte[][] valBytes = new byte[attrs.size()][];
        int kvLen = 0;
        int i = 0;
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            nameBytes[i] = utf8(e.getKey());
            valBytes[i] = utf8(e.getValue());
            kvLen += 2 + nameBytes[i].length + 4 + valBytes[i].length;
            i++;
        }
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + kvLen + data.length);
        buf.put((byte) MAGIC);
        buf.put((byte) VERSION);
        buf.put((byte) msgType);
        buf.put((byte) flags);
        buf.putInt(seq);
        buf.putShort((short) attrs.size());
        buf.putInt(data.length);
        for (int j = 0; j < nameBytes.length; j++) {
            buf.putShort((short) nameBytes[j].length);
            buf.put(nameBytes[j]);
            buf.putInt(valBytes[j].length);
            buf.put(valBytes[j]);
        }
        buf.put(data);
        return buf.array();
    }

    public static EventMeshFrame decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    public static EventMeshFrame decode(byte[] bytes, int offset, int length) {
        if (length < HEADER_LEN) {
            throw new IllegalArgumentException("frame too short: " + length);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes, offset, length);
        int magic = buf.get() & 0xFF;
        if (magic != MAGIC) {
            throw new IllegalArgumentException("bad frame magic: 0x" + Integer.toHexString(magic));
        }
        int ver = buf.get() & 0xFF;
        if (ver != VERSION) {
            throw new IllegalArgumentException("unsupported frame version: " + ver);
        }
        int msgType = buf.get() & 0xFF;
        int flags = buf.get() & 0xFF;
        int seq = buf.getInt();
        int keyCount = buf.getShort() & 0xFFFF;
        int dataLen = buf.getInt();
        Map<String, String> attrs = new LinkedHashMap<>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            String name = getString(buf, buf.getShort() & 0xFFFF);
            String value = getString(buf, buf.getInt());
            attrs.put(name, value);
        }
        byte[] data = new byte[dataLen];
        buf.get(data);
        return new EventMeshFrame(msgType, flags, seq, attrs, data);
    }

    // -------------------- accessors --------------------

    public int msgType() {
        return msgType;
    }

    public boolean isStreamRequest() {
        return msgType == TYPE_STREAM_REQ;
    }

    public boolean isStreamChunk() {
        return msgType == TYPE_STREAM_CHUNK;
    }

    public boolean isEvent() {
        return msgType == TYPE_EVENT;
    }

    public boolean isDone() {
        return (flags & FLAG_DONE) != 0;
    }

    public Map<String, String> attributes() {
        return attrs;
    }

    public byte[] data() {
        return data;
    }

    // -------------------- helpers --------------------

    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    private static String str(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String getString(ByteBuffer buf, int len) {
        byte[] b = new byte[len];
        buf.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
