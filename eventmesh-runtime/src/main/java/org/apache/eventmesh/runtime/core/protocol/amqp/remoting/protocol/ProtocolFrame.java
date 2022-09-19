package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol;

import io.netty.buffer.ByteBuf;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpProtocolClassException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpProtocolHeaderException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpProtocolVersionException;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;

import java.nio.charset.StandardCharsets;

public class ProtocolFrame implements AMQData {

    private static final byte[] AMQP_HEADER = new byte[] {(byte) 'A', (byte) 'M', (byte) 'Q', (byte) 'P'};
    private static final byte CURRENT_PROTOCOL_CLASS = 0;
    public static final int PROTOCOL_FRAME_LENGTH = 8;

    private byte[] protocolHeader = AMQP_HEADER;
    private byte protocolClass = CURRENT_PROTOCOL_CLASS;
    private final byte protocolMajor;
    private final byte protocolMinor;
    private final byte protocolRevision;

    public ProtocolFrame(byte[] protocolHeader, byte protocolClass, byte protocolMajor, byte protocolMinor,
                         byte protocolRevision) {
        this.protocolHeader = protocolHeader;
        this.protocolClass = protocolClass;
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.protocolRevision = protocolRevision;
    }

    public ProtocolFrame(byte protocolMajor, byte protocolMinor, byte protocolRevision) {
        this.protocolHeader = AMQP_HEADER;
        this.protocolClass = CURRENT_PROTOCOL_CLASS;
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.protocolRevision = protocolRevision;
    }

    public ProtocolFrame(ProtocolVersion protocolVersion) {
        this.protocolMajor = protocolVersion.getProtocolMajor();
        this.protocolMinor = protocolVersion.getProtocolMinor();
        this.protocolRevision = protocolVersion.getProtocolRevision();
    }

    public static ProtocolFrame decode(ByteBuf in) {
        byte[] protocolHeader = new byte[4];
        in.readBytes(protocolHeader);

        byte protocolClass = in.readByte();
        byte protocolMajor = in.readByte();
        byte protocolMinor = in.readByte();
        byte protocolRevision = in.readByte();
        return new ProtocolFrame(protocolHeader, protocolClass, protocolMajor, protocolMinor, protocolRevision);
    }

    public long getSize() {
        return PROTOCOL_FRAME_LENGTH;
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeBytes(AMQP_HEADER);
        buf.writeByte(protocolClass);
        buf.writeByte(protocolMajor);
        buf.writeByte(protocolMinor);
        buf.writeByte(protocolRevision);
    }

    public ProtocolVersion checkVersion() throws Exception {

        if (protocolHeader.length != 4) {
            throw new AmqpProtocolHeaderException("Protocol header should have exactly four octets");
        }
        for (int i = 0; i < 4; i++) {
            if (protocolHeader[i] != AMQP_HEADER[i]) {
                throw new AmqpProtocolHeaderException("Protocol header is not correct: Got "
                    + new String(protocolHeader, StandardCharsets.ISO_8859_1)
                    + " should be: "
                    + new String(AMQP_HEADER, StandardCharsets.ISO_8859_1));
            }
        }

        ProtocolVersion pv;

        // Hack for 0-9-1 which changed how the header was defined
        if (protocolMajor == 0 && protocolMinor == 9 && protocolRevision == 1) {
            pv = ProtocolVersion.v0_91;
            if (protocolClass != 0) {
                throw new AmqpProtocolClassException("Protocol class " + 0 + " was expected; received " +
                    protocolClass);
            }
        } else {
            pv = new ProtocolVersion(protocolClass, protocolMajor, protocolMinor, protocolRevision);
        }

        if (!ProtocolVersion.checkVersionSupport(pv)) {
            // TODO: add list of available versions in list to msg...
            throw new AmqpProtocolVersionException("Protocol version " + protocolMajor + "."
                + protocolMinor
                + " not supported.");
        }
        return pv;
    }

    public byte[] getProtocolHeader() {
        return protocolHeader;
    }

    public byte getProtocolClass() {
        return protocolClass;
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

    @Override
    public void recycle() {
        // do nothing
    }
}
