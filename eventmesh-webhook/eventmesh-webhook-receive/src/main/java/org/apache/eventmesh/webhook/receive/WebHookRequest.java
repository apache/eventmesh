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

package org.apache.eventmesh.webhook.receive;

public class WebHookRequest {

    /**
     * manufacturer event id
     */
    private String manufacturerEventId;

    /**
     * manufacturer event name
     */
    private String manufacturerEventName;


    /**
     * manufacturer name
     */
    private String manufacturerSource;

    /**
     * webhook request body
     */
    private byte[] data;

    public void setManufacturerEventId(String manufacturerEventId) {
        this.manufacturerEventId = manufacturerEventId;
    }

    public void setManufacturerEventName(String manufacturerEventName) {
        this.manufacturerEventName = manufacturerEventName;
    }

    public void setManufacturerSource(String manufacturerSource) {
        this.manufacturerSource = manufacturerSource;
    }

    public void setData(byte[] newData) {
        if (newData == null || newData.length == 0) {
            return;
        }

        int len = newData.length;
        this.data = new byte[len];
        System.arraycopy(newData, 0, this.data, 0, len);
    }


    public String getManufacturerEventId() {
        return manufacturerEventId;
    }

    public String getManufacturerEventName() {
        return manufacturerEventName;
    }

    public String getManufacturerSource() {
        return manufacturerSource;
    }

    public byte[] getData() {

        if (data == null) {
            return new byte[0];
        }

        int len = data.length;
        byte[] b = new byte[len];
        System.arraycopy(data, 0, b, 0, len);
        return b;
    }


}
