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

package org.apache.eventmesh.common.protocol.http.common;

public enum RequestURI {

    PUBLISH("/eventmesh/publish", "PUBLISH SINGLE EVENT"),

    PUBLISH_BRIDGE("/eventmesh/bridge/publish", "PUBLISH REMOTE SINGLE EVENT"),

    PUBLISH_BATCH("/eventmesh/publish/batch", "PUBLISH BATCH EVENTS"),

    SUBSCRIBE_LOCAL("/eventmesh/subscribe/local", "SUBSCRIBE LOCAL"),

    SUBSCRIBE_REMOTE("/eventmesh/subscribe/remote", "SUBSCRIBE REMOTE"),

    UNSUBSCRIBE_LOCAL("/eventmesh/unsubscribe/local", "SUBSCRIBE LOCAL"),

    UNSUBSCRIBE_REMOTE("/eventmesh/unsubscribe/remote", "SUBSCRIBE REMOTE");


    private String requestURI;

    private String desc;

    RequestURI(String requestURI, String desc) {
        this.requestURI = requestURI;
        this.desc = desc;
    }

    public static boolean contains(String requestURI) {
        boolean flag = false;
        for (RequestURI itr : RequestURI.values()) {
            if (itr.requestURI.equals(requestURI)) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    public static RequestURI get(String requestURI) {
        RequestURI ret = null;
        for (RequestURI itr : RequestURI.values()) {
            if (itr.requestURI.equals(requestURI)) {
                ret = itr;
                break;
            }
        }
        return ret;
    }

    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
