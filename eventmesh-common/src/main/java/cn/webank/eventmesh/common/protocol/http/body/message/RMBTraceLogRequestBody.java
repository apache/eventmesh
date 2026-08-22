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

package cn.webank.eventmesh.common.protocol.http.body.message;

import cn.webank.eventmesh.common.protocol.http.body.Body;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class RMBTraceLogRequestBody extends Body {

    /////////////////////////////////RMB-TRACE////////////////////////////
    public static final String LOG_LEVEL = "level";
    public static final String LOG_POINT = "logPoint";
    public static final String LOG_MESSAGE = "message";
    public static final String LOG_MODEL = "model";
    public static final String LOG_RETCODE = "retCode";
    public static final String LOG_RETMSG = "retMsg";
    public static final String LOG_LANG = "lang";
    public static final String LOG_EXTFIELDS = "extFields";

    private String level;

    private String logPoint;

    private String message;

    private String model;

    private String retCode;

    private String retMsg;

    private String lang;

    private HashMap<String, String> extFields = new HashMap<String, String>();

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLogPoint() {
        return logPoint;
    }

    public void setLogPoint(String logPoint) {
        this.logPoint = logPoint;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public HashMap<String, String> getExtFields() {
        return extFields;
    }

    public void setExtFields(HashMap<String, String> extFields) {
        this.extFields = extFields;
    }

    public static RMBTraceLogRequestBody buildBody(Map<String, Object> bodyParam) {
        RMBTraceLogRequestBody body = new RMBTraceLogRequestBody();
        body.setLang(MapUtils.getString(bodyParam, LOG_LANG));
        body.setLevel(MapUtils.getString(bodyParam, LOG_LEVEL));
        body.setLogPoint(MapUtils.getString(bodyParam, LOG_POINT));
        body.setMessage(MapUtils.getString(bodyParam, LOG_MESSAGE));
        body.setModel(MapUtils.getString(bodyParam, LOG_MODEL));
        body.setRetCode(MapUtils.getString(bodyParam, LOG_RETCODE));
        body.setRetMsg(MapUtils.getString(bodyParam, LOG_RETMSG));
        String extFields = MapUtils.getString(bodyParam, LOG_EXTFIELDS);
        if (StringUtils.isNotBlank(extFields)) {
            body.setExtFields(JSONObject.parseObject(extFields, HashMap.class));
        }
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(LOG_EXTFIELDS, extFields);
        map.put(LOG_LANG, lang);
        map.put(LOG_LEVEL, level);
        map.put(LOG_MESSAGE, message);
        map.put(LOG_RETMSG, retMsg);
        map.put(LOG_MODEL, model);
        map.put(LOG_POINT, logPoint);
        map.put(LOG_RETCODE, retCode);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RMBTraceLogRequestBody={")
                .append("level=").append(level).append(",")
                .append("logPoint=").append(logPoint).append(",")
                .append("message=").append(message).append(",")
                .append("model=").append(model).append(",")
                .append("retCode=").append(retCode).append(",")
                .append("retMsg=").append(retMsg).append(",")
                .append("lang=").append(lang).append(",")
                .append("extFields=").append(extFields).append("}");
        return sb.toString();
    }
}
