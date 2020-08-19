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

package com.webank.eventmesh.common.protocol.tcp;

public class Header {

    private Command cmd;
    private int code;
    private String msg;
    private String seq;

    public Header() {
    }

    public Header(Command cmd, int code, String msg, String seq) {
        this.cmd = cmd;
        this.code = code;
        this.msg = msg;
        this.seq = seq;
    }

    public Command getCommand() {
        return cmd;
    }

    public void setCommand(Command cmd) {
        this.cmd = cmd;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    @Override
    public String toString() {
        return "Header{" +
                "cmd=" + cmd +
                ", code=" + code +
                ", msg='" + msg + '\'' +
                ", seq='" + seq + '\'' +
                '}';
    }
}
