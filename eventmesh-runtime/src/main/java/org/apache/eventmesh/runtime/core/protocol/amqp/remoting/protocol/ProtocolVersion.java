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
package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ProtocolVersion {

    private byte[] protocolHeader = new byte[] {(byte) 'A', (byte) 'M', (byte) 'Q', (byte) 'P'};
    private byte protocolClass = 0;
    private byte protocolMajor;
    private byte protocolMinor;
    private byte protocolRevision = 0;
    public static final ProtocolVersion v0_91 = new ProtocolVersion((byte) 0, (byte) 9, (byte) 1);
    public static final ProtocolVersion v0_8 = new ProtocolVersion((byte) 0, (byte) 8, (byte) 0);
    public static final ProtocolVersion v0_9 = new ProtocolVersion((byte) 1, (byte) 1, (byte) 0, (byte) 9);
    public static final ProtocolVersion v1_0 = new ProtocolVersion((byte) 1, (byte) 0, (byte) 0);
    private static final Set<ProtocolVersion> protocols = new HashSet<>(2);

   static  {

       protocols.add(v0_91);
       protocols.add(v0_9);
    }

    public static boolean checkVersionSupport(ProtocolVersion pv){
       return protocols.contains(pv);
    }


    public ProtocolVersion(byte[] protocolHeader, byte protocolClass, byte protocolMajor, byte protocolMinor,
                           byte protocolRevision) {
        this.protocolHeader = protocolHeader;
        this.protocolClass = protocolClass;
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.protocolRevision = protocolRevision;
    }

    public ProtocolVersion(byte protocolClass, byte protocolMajor, byte protocolMinor, byte protocolRevision) {
        this.protocolClass = protocolClass;
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.protocolRevision = protocolRevision;
    }

    public ProtocolVersion(byte protocolMajor, byte protocolMinor, byte protocolRevision) {
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.protocolRevision = protocolRevision;
    }

    public static ProtocolVersion fromBytes(byte[] bytes) {
        byte[] protocolHeader = Arrays.copyOfRange(bytes, 0, 4);
        byte protocolClass = bytes[4];
        byte protocolMajor = bytes[5];
        byte protocolMinor = bytes[6];
        byte protocolRevision = bytes[7];
        return new ProtocolVersion(protocolHeader, protocolClass, protocolMajor, protocolMinor, protocolRevision);
    }

    public byte getProtocolMajor() {
        return protocolMajor;
    }

    public byte getProtocolMinor() {
        return protocolMinor;
    }

    public byte getProtocolRevision() {
        return protocolRevision;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProtocolVersion version = (ProtocolVersion) o;
        return protocolClass == version.protocolClass &&
            protocolMajor == version.protocolMajor &&
            protocolMinor == version.protocolMinor &&
            protocolRevision == version.protocolRevision &&
            Arrays.equals(protocolHeader, version.protocolHeader);
    }

    @Override public int hashCode() {
        int result = Objects.hash(protocolClass, protocolMajor, protocolMinor, protocolRevision);
        result = 31 * result + Arrays.hashCode(protocolHeader);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(new String(protocolHeader));
        builder.append(" " + protocolClass);
        builder.append("-" + protocolMajor);
        builder.append("-" + protocolMinor);
        builder.append("-" + protocolRevision);
        return builder.toString();
    }
}