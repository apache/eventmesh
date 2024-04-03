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

package org.apache.eventmesh.runtime.util;

import org.apache.commons.lang3.ArrayUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.Channel;

public abstract class RemotingHelper {

    public static String exceptionSimpleDesc(final Throwable e) {
        final StringBuilder sb = new StringBuilder();
        if (e != null) {
            sb.append(e);

            final StackTraceElement[] stackTrace = e.getStackTrace();
            if (ArrayUtils.isNotEmpty(stackTrace)) {
                sb.append(", ").append(stackTrace[0]);
            }
        }

        return sb.toString();
    }

    public static SocketAddress string2SocketAddress(final String addr) {
        final int split = addr.lastIndexOf(':');
        final String host = addr.substring(0, split);
        final String port = addr.substring(split + 1);
        return new InetSocketAddress(host, Integer.parseInt(port));
    }

    public static String parseChannelRemoteAddr(final Channel channel) {
        if (channel == null) {
            return "";
        }

        final String addr = channel.remoteAddress() != null ? channel.remoteAddress().toString() : "";

        if (addr.length() > 0) {
            final int index = addr.lastIndexOf('/');
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }

        return "";
    }

    public static String parseChannelLocalAddr(final Channel channel) {
        if (channel == null) {
            return "";
        }

        final String addr = channel.localAddress() != null ? channel.localAddress().toString() : "";

        if (addr.length() > 0) {
            final int index = addr.lastIndexOf('/');
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }

        return "";
    }

    public static String parseSocketAddressAddr(final InetSocketAddress socketAddress) {
        return socketAddress != null ? socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort() : "";
    }

}
