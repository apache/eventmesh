package com.webank.eventmesh.runtime.util;

import io.openmessaging.Message;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageClientIDSetter {

    public static final String PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX = "UNIQ_KEY";
    private static final String TOPIC_KEY_SPLITTER = "#";
    private static final int LEN;
    private static final String FIX_STRING;
    private static final AtomicInteger COUNTER;
    private static long startTime;
    private static long nextStartTime;

    final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    static {
        LEN = 4 + 2 + 4 + 4 + 2;
        ByteBuffer tempBuffer = ByteBuffer.allocate(10);
        tempBuffer.position(2);
        tempBuffer.putInt(getPid());
        tempBuffer.position(0);
        try {
            tempBuffer.put(getIP());
        } catch (Exception e) {
            tempBuffer.put(createFakeIP());
        }
        tempBuffer.position(6);
        tempBuffer.putInt(MessageClientIDSetter.class.getClassLoader().hashCode());
        FIX_STRING = bytes2string(tempBuffer.array());
        setStartTime(System.currentTimeMillis());
        COUNTER = new AtomicInteger(0);
    }

    public static void setUniqID(final Message msg) {
        if (msg.userHeaders().getString(PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX) == null) {
            msg.userHeaders().put(PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX, createUniqID());
        }
    }

    public static String createUniqID() {
        StringBuilder sb = new StringBuilder(LEN * 2);
        sb.append(FIX_STRING);
        sb.append(bytes2string(createUniqIDBuffer()));
        return sb.toString();
    }

    public static String bytes2string(byte[] src) {
        char[] hexChars = new char[src.length * 2];
        for (int j = 0; j < src.length; j++) {
            int v = src[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private synchronized static void setStartTime(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        startTime = cal.getTimeInMillis();
        cal.add(Calendar.MONTH, 1);
        nextStartTime = cal.getTimeInMillis();
    }

    private static byte[] createUniqIDBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 2);
        long current = System.currentTimeMillis();
        if (current >= nextStartTime) {
            setStartTime(current);
        }
        buffer.position(0);
        buffer.putInt((int) (System.currentTimeMillis() - startTime));
        buffer.putShort((short) COUNTER.getAndIncrement());
        return buffer.array();
    }

    public static byte[] createFakeIP() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putLong(System.currentTimeMillis());
        bb.position(4);
        byte[] fakeIP = new byte[4];
        bb.get(fakeIP);
        return fakeIP;
    }

    public static int getPid() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        String name = runtime.getName(); // format: "pid@hostname"
        try {
            return Integer.parseInt(name.substring(0, name.indexOf('@')));
        } catch (Exception e) {
            return -1;
        }
    }

    public static byte[] getIP() {
        try {
            Enumeration allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            byte[] internalIP = null;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                Enumeration addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    ip = (InetAddress) addresses.nextElement();
                    if (ip != null && ip instanceof Inet4Address) {
                        byte[] ipByte = ip.getAddress();
                        if (ipByte.length == 4) {
                            if (ipCheck(ipByte)) {
                                if (!isInternalIP(ipByte)) {
                                    return ipByte;
                                } else if (internalIP == null) {
                                    internalIP = ipByte;
                                }
                            }
                        }
                    }
                }
            }
            if (internalIP != null) {
                return internalIP;
            } else {
                throw new RuntimeException("Can not get local ip");
            }
        } catch (Exception e) {
            throw new RuntimeException("Can not get local ip", e);
        }
    }

    public static boolean isInternalIP(byte[] ip) {
        if (ip.length != 4) {
            throw new RuntimeException("illegal ipv4 bytes");
        }

        //10.0.0.0~10.255.255.255
        //172.16.0.0~172.31.255.255
        //192.168.0.0~192.168.255.255
        if (ip[0] == (byte) 10) {

            return true;
        } else if (ip[0] == (byte) 172) {
            if (ip[1] >= (byte) 16 && ip[1] <= (byte) 31) {
                return true;
            }
        } else if (ip[0] == (byte) 192) {
            if (ip[1] == (byte) 168) {
                return true;
            }
        }
        return false;
    }

    private static boolean ipCheck(byte[] ip) {
        if (ip.length != 4) {
            throw new RuntimeException("illegal ipv4 bytes");
        }

//        if (ip[0] == (byte)30 && ip[1] == (byte)10 && ip[2] == (byte)163 && ip[3] == (byte)120) {
//        }

        if (ip[0] >= (byte) 1 && ip[0] <= (byte) 126) {
            if (ip[1] == (byte) 1 && ip[2] == (byte) 1 && ip[3] == (byte) 1) {
                return false;
            }
            if (ip[1] == (byte) 0 && ip[2] == (byte) 0 && ip[3] == (byte) 0) {
                return false;
            }
            return true;
        } else if (ip[0] >= (byte) 128 && ip[0] <= (byte) 191) {
            if (ip[2] == (byte) 1 && ip[3] == (byte) 1) {
                return false;
            }
            if (ip[2] == (byte) 0 && ip[3] == (byte) 0) {
                return false;
            }
            return true;
        } else if (ip[0] >= (byte) 192 && ip[0] <= (byte) 223) {
            if (ip[3] == (byte) 1) {
                return false;
            }
            if (ip[3] == (byte) 0) {
                return false;
            }
            return true;
        }
        return false;
    }

}
