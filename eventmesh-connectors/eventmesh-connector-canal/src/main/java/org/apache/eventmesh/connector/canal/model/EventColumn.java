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

package org.apache.eventmesh.connector.canal.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

public class EventColumn implements Serializable {

    @Setter
    @Getter
    private int index;

    @Getter
    @Setter
    private int columnType;

    @Getter
    @Setter
    private String columnName;

    /**
     * timestamp,Datetime is long
     */
    @Setter
    private String columnValue;

    private boolean isNull;

    private boolean isKey;

    private boolean isUpdate = true;

    public String getColumnValue() {
        if (isNull) {
            columnValue = null;
            return null;
        } else {
            return columnValue;
        }
    }

    public boolean isNull() {
        return isNull;
    }

    public void setNull(boolean isNull) {
        this.isNull = isNull;
    }

    public boolean isKey() {
        return isKey;
    }

    public void setKey(boolean isKey) {
        this.isKey = isKey;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public void setUpdate(boolean isUpdate) {
        this.isUpdate = isUpdate;
    }

    public EventColumn clone() {
        EventColumn column = new EventColumn();
        column.setIndex(index);
        column.setColumnName(columnName);
        column.setColumnType(columnType);
        column.setColumnValue(columnValue);
        column.setKey(isKey);
        column.setNull(isNull);
        column.setUpdate(isUpdate);
        return column;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((columnName == null) ? 0 : columnName.hashCode());
        result = prime * result + columnType;
        result = prime * result + ((columnValue == null) ? 0 : columnValue.hashCode());
        result = prime * result + index;
        result = prime * result + (isKey ? 1231 : 1237);
        result = prime * result + (isNull ? 1231 : 1237);
        result = prime * result + (isUpdate ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        EventColumn other = (EventColumn) obj;
        if (columnName == null) {
            if (other.columnName != null) {
                return false;
            }
        } else if (!columnName.equals(other.columnName)) {
            return false;
        }
        if (columnType != other.columnType) {
            return false;
        }
        if (columnValue == null) {
            if (other.columnValue != null) {
                return false;
            }
        } else if (!columnValue.equals(other.columnValue)) {
            return false;
        }
        if (index != other.index) {
            return false;
        }
        if (isKey != other.isKey) {
            return false;
        }
        if (isNull != other.isNull) {
            return false;
        }
        return isUpdate == other.isUpdate;
    }

    @Override
    public String toString() {
        return "EventColumn{"
            + "index=" + index
            + ", columnType=" + columnType
            + ", columnName='" + columnName + '\''
            + ", columnValue='" + columnValue + '\''
            + ", isNull=" + isNull
            + ", isKey=" + isKey
            + ", isUpdate=" + isUpdate
            + '}';
    }
}
