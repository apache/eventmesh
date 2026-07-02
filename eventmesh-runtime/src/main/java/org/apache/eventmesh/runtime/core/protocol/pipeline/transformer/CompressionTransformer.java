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

package org.apache.eventmesh.runtime.core.protocol.pipeline.transformer;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Compression transformer — gzip-compresses event data body.
 *
 * <p>Triggered when event data size exceeds the threshold set in pipeline context:
 * <pre>{@code
 *   ctx.setAttribute("CompressionTransformer.threshold", 10240); // 10KB
 * }</pre>
 *
 * <p>Compressed data is base64-encoded and the extension
 * {@code eventmesh_compressed} is set to "gzip".
 */
@Slf4j
public class CompressionTransformer implements PipelineTransformer {

    private static final String THRESHOLD_ATTR = "CompressionTransformer.threshold";
    private static final int DEFAULT_THRESHOLD_BYTES = 10 * 1024; // 10KB
    private static final String EXT_COMPRESSED = "eventmesh_compressed";

    @Override
    public String name() {
        return "compression";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public CloudEvent transform(CloudEvent event, PipelineContext ctx) {
        int threshold = getThreshold(ctx);

        if (event.getData() == null) {
            return event;
        }

        byte[] dataBytes = event.getData().toBytes();
        if (dataBytes.length < threshold) {
            log.trace("CompressionTransformer: data size {} < threshold {}, skip",
                dataBytes.length, threshold);
            return event;
        }

        try {
            byte[] compressed = gzipCompress(dataBytes);
            String encoded = Base64.getEncoder().encodeToString(compressed);

            int originalSize = dataBytes.length;
            double ratio = (1.0 - (double) compressed.length / originalSize) * 100;

            log.debug("CompressionTransformer: compressed {} bytes -> {} bytes ({}%)",
                originalSize, compressed.length, String.format("%.1f", ratio));

            return CloudEventBuilder.from(event)
                    .withData(encoded.getBytes(StandardCharsets.UTF_8))
                    .withExtension(EXT_COMPRESSED, "gzip")
                    .withExtension("eventmesh_uncompressed_size", String.valueOf(originalSize))
                    .build();
        } catch (Exception e) {
            log.warn("CompressionTransformer: failed to compress, pass-through", e);
            return event;
        }
    }

    int getThreshold(PipelineContext ctx) {
        try {
            Object attr = ctx.getAttribute(THRESHOLD_ATTR);
            if (attr instanceof Number) {
                return ((Number) attr).intValue();
            }
            if (attr instanceof String) {
                return Integer.parseInt((String) attr);
            }
        } catch (Exception e) {
            log.debug("CompressionTransformer: using default threshold {}", DEFAULT_THRESHOLD_BYTES);
        }
        return DEFAULT_THRESHOLD_BYTES;
    }

    static byte[] gzipCompress(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }
}
