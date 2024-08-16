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

package org.apache.eventmesh.connector.http.sink.handler;

import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

/**
 * Interface for handling ConnectRecords via HTTP or HTTPS. Classes implementing this interface are responsible for processing ConnectRecords by
 * sending them over HTTP or HTTPS, with additional support for handling multiple requests and asynchronous processing.
 *
 * <p>Any class that needs to process ConnectRecords via HTTP or HTTPS should implement this interface.
 * Implementing classes must provide implementations for the {@link #start()}, {@link #handle(ConnectRecord)},
 * {@link #deliver(URI, HttpConnectRecord, Map)}, and {@link #stop()} methods.</p>
 *
 * <p>Implementing classes should ensure thread safety and handle HTTP/HTTPS communication efficiently.
 * The {@link #start()} method initializes any necessary resources for HTTP/HTTPS communication. The {@link #handle(ConnectRecord)} method processes a
 * ConnectRecord by sending it over HTTP or HTTPS. The {@link #deliver(URI, HttpConnectRecord, Map)} method processes HttpConnectRecord on specified
 * URL while returning its own processing logic {@link #stop()} method releases any resources used for HTTP/HTTPS communication.</p>
 *
 * <p>It's recommended to handle exceptions gracefully within the {@link #deliver(URI, HttpConnectRecord, Map)} method
 * to prevent message loss or processing interruptions.</p>
 */
public interface HttpSinkHandler {

    /**
     * Initializes the HTTP/HTTPS handler. This method should be called before using the handler.
     */
    void start();

    /**
     * Processes a ConnectRecord by sending it over HTTP or HTTPS. This method should be called for each ConnectRecord that needs to be processed.
     *
     * @param record the ConnectRecord to process
     */
    void handle(ConnectRecord record);


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @param attributes        additional attributes to be used in processing
     * @return processing chain
     */
    Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord, Map<String, Object> attributes);

    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler. This method should be called when the handler is no longer needed.
     */
    void stop();
}

