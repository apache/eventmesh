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

package cn.webank.eventmesh.common.protocol.tcp;

public class UserAgent {

    private String env;
    private String subsystem;
    private String dcn;
    private String path;
    private int pid;
    private String host;
    private int port;
    private String version;
    private String username;
    private String password;
    private String idc;
    private String purpose;
    private int unack = 0;

    public UserAgent() {
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getDcn() {
        return dcn;
    }

    public void setDcn(String dcn) {
        this.dcn = dcn;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIdc() {
        return idc;
    }

    public void setIdc(String idc) {
        this.idc = idc;
    }

    public int getUnack() {
        return unack;
    }

    public void setUnack(int unack) {
        this.unack = unack;
    }

    @Override
    public String toString() {
        return "UserAgent{" +
                "env='" + env + '\'' +
                "subsystem='" + subsystem + '\'' +
                ", dcn='" + dcn + '\'' +
                ", path='" + path + '\'' +
                ", pid=" + pid +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", version='" + version + '\'' +
                ", idc='" + idc + '\'' +
                ", purpose='" + purpose + '\'' +
                ", unack='" + unack + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserAgent userAgent = (UserAgent) o;

        if (pid != userAgent.pid) return false;
        if (port != userAgent.port) return false;
        if (unack != userAgent.unack) return false;
        if (subsystem != null ? !subsystem.equals(userAgent.subsystem) : userAgent.subsystem != null) return false;
        if (dcn != null ? !dcn.equals(userAgent.dcn) : userAgent.dcn != null) return false;
        if (path != null ? !path.equals(userAgent.path) : userAgent.path != null) return false;
        if (host != null ? !host.equals(userAgent.host) : userAgent.host != null) return false;
        if (purpose != null ? !purpose.equals(userAgent.purpose) : userAgent.purpose != null) return false;
        if (version != null ? !version.equals(userAgent.version) : userAgent.version != null) return false;
        if (username != null ? !username.equals(userAgent.username) : userAgent.username != null) return false;
        if (password != null ? !password.equals(userAgent.password) : userAgent.password != null) return false;
        if (env != null ? !env.equals(userAgent.env) : userAgent.env != null) return false;
        return idc != null ? idc.equals(userAgent.idc) : userAgent.idc == null;
    }

    @Override
    public int hashCode() {
        int result = subsystem != null ? subsystem.hashCode() : 0;
        result = 31 * result + (dcn != null ? dcn.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + pid;
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + (purpose != null ? purpose.hashCode() : 0);
        result = 31 * result + port;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (idc != null ? idc.hashCode() : 0);
        result = 31 * result + (env != null ? env.hashCode() : 0);
        result = 31 * result + unack;
        return result;
    }
}
