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

package org.apache.eventmesh.openconnect.offsetmgmt.api.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RecordOffset {

    /**
     * if pull message from mq key=queueOffset,
     * value=queueOffset value
     */
    private Map<String, ?> offset = new HashMap<>();

    public RecordOffset() {

    }

    public RecordOffset(Map<String, ?> offset) {
        this.offset = offset;
    }

    public Map<String, ?> getOffset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecordOffset)) {
            return false;
        }
        RecordOffset offset1 = (RecordOffset) o;
        return Objects.equals(offset, offset1.offset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset);
    }

}
