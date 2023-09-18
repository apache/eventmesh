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

package org.apache.eventmesh.common.protocol.catalog.protos;

import java.util.Objects;

import com.google.protobuf.ByteString;

/**
 * Protobuf type {@code eventmesh.catalog.api.protocol.Operation}
 */
@SuppressWarnings({"all"})
public final class Operation
        extends
            com.google.protobuf.GeneratedMessageV3
        implements
            // @@protoc_insertion_point(message_implements:eventmesh.catalog.api.protocol.Operation)
            OperationOrBuilder {

    private static final long serialVersionUID = 7231240618302324570L;

    public static final int CHANNEL_NAME_FIELD_NUMBER = 1;
    public static final int SCHEMA_FIELD_NUMBER = 2;
    public static final int TYPE_FIELD_NUMBER = 3;

    private static final Operation DEFAULT_INSTANCE;

    private volatile String channelName;
    private volatile String schema;
    private volatile String type;
    private byte memoizedIsInitialized = -1;

    // Use Operation.newBuilder() to construct.

    static {
        DEFAULT_INSTANCE = new Operation();
    }

    public static Operation getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    private Operation(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
        super(builder);
    }

    private Operation() {
        channelName = "";
        schema = "";
        type = "";
    }

    @Override
    @SuppressWarnings({"unused"})
    protected Object newInstance(UnusedPrivateParameter unused) {
        return new Operation();
    }

    @Override
    public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
        return this.unknownFields;
    }

    private Operation(
                      com.google.protobuf.CodedInputStream input,
                      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                                                                                   throws com.google.protobuf.InvalidProtocolBufferException {

        this();
        Objects.requireNonNull(input, "CodedInputStream can not be null");
        Objects.requireNonNull(extensionRegistry, "ExtensionRegistryLite can not be null");

        com.google.protobuf.UnknownFieldSet.Builder unknownFields =
                com.google.protobuf.UnknownFieldSet.newBuilder();
        try {
            boolean done = false;
            while (!done) {
                int tag = input.readTag();
                switch (tag) {
                    case 0:
                        done = true;
                        break;
                    case 10: {
                        channelName = input.readStringRequireUtf8();
                        break;
                    }
                    case 18: {
                        schema = input.readStringRequireUtf8();
                        break;
                    }
                    case 26: {
                        type = input.readStringRequireUtf8();
                        break;
                    }
                    default: {
                        if (!parseUnknownField(
                                input, unknownFields, extensionRegistry, tag)) {
                            done = true;
                        }
                        break;
                    }
                }
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw e.setUnfinishedMessage(this);
        } catch (java.io.IOException e) {
            throw new com.google.protobuf.InvalidProtocolBufferException(
                    e).setUnfinishedMessage(this);
        } finally {
            this.unknownFields = unknownFields.build();
            makeExtensionsImmutable();
        }
    }

    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
        return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_Operation_descriptor;
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_Operation_fieldAccessorTable
                .ensureFieldAccessorsInitialized(
                        Operation.class, Builder.class);
    }

    /**
     * <code>string channel_name = 1;</code>
     *
     * @return The channelName.
     */
    @Override
    public String getChannelName() {
        return channelName;
    }

    /**
     * <code>string channel_name = 1;</code>
     *
     * @return The bytes for channelName.
     */
    @Override
    public com.google.protobuf.ByteString getChannelNameBytes() {
        return ByteString.copyFromUtf8(channelName);
    }

    /**
     * <code>string schema = 2;</code>
     *
     * @return The schema.
     */
    @Override
    public String getSchema() {
        return schema;
    }

    /**
     * <code>string schema = 2;</code>
     *
     * @return The bytes for schema.
     */
    @Override
    public com.google.protobuf.ByteString getSchemaBytes() {

        return ByteString.copyFromUtf8(schema);
    }

    /**
     * <pre>
     * publish/subscribe
     * </pre>
     *
     * <code>string type = 3;</code>
     *
     * @return The type.
     */
    @Override
    public String getType() {

        return type;
    }

    /**
     * <pre>
     * publish/subscribe
     * </pre>
     *
     * <code>string type = 3;</code>
     *
     * @return The bytes for type.
     */
    @Override
    public com.google.protobuf.ByteString getTypeBytes() {

        return ByteString.copyFromUtf8(type);
    }

    @Override
    public final boolean isInitialized() {
        byte isInitialized = memoizedIsInitialized;
        if (isInitialized == 1) {
            return true;
        }
        if (isInitialized == 0) {
            return false;
        }

        memoizedIsInitialized = 1;
        return true;
    }

    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
        if (!getChannelNameBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 1, channelName);
        }
        if (!getSchemaBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 2, schema);
        }
        if (!getTypeBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 3, type);
        }
        unknownFields.writeTo(output);
    }

    @Override
    public int getSerializedSize() {
        int size = memoizedSize;
        if (size != -1) {
            return size;
        }

        size = 0;
        if (!getChannelNameBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, channelName);
        }
        if (!getSchemaBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, schema);
        }
        if (!getTypeBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, type);
        }
        size += unknownFields.getSerializedSize();
        memoizedSize = size;
        return size;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Operation)) {
            return super.equals(obj);
        }
        Operation other = (Operation) obj;

        if (!getChannelName()
                .equals(other.getChannelName())) {
            return false;
        }
        if (!getSchema()
                .equals(other.getSchema())) {
            return false;
        }
        if (!getType()
                .equals(other.getType())) {
            return false;
        }
        if (!unknownFields.equals(other.unknownFields)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (memoizedHashCode != 0) {
            return memoizedHashCode;
        }
        int hash = 41;
        hash = (19 * hash) + getDescriptor().hashCode();
        hash = (37 * hash) + CHANNEL_NAME_FIELD_NUMBER;
        hash = (53 * hash) + getChannelName().hashCode();
        hash = (37 * hash) + SCHEMA_FIELD_NUMBER;
        hash = (53 * hash) + getSchema().hashCode();
        hash = (37 * hash) + TYPE_FIELD_NUMBER;
        hash = (53 * hash) + getType().hashCode();
        hash = (29 * hash) + unknownFields.hashCode();
        memoizedHashCode = hash;
        return hash;
    }

    public static Operation parseFrom(
                                      java.nio.ByteBuffer data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static Operation parseFrom(
                                      java.nio.ByteBuffer data,
                                      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static Operation parseFrom(
                                      com.google.protobuf.ByteString data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static Operation parseFrom(
                                      com.google.protobuf.ByteString data,
                                      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static Operation parseFrom(byte[] data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static Operation parseFrom(
                                      byte[] data,
                                      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static Operation parseFrom(java.io.InputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }

    public static Operation parseFrom(
                                      java.io.InputStream input,
                                      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public static Operation parseDelimitedFrom(java.io.InputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input);
    }

    public static Operation parseDelimitedFrom(
                                               java.io.InputStream input,
                                               com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }

    public static Operation parseFrom(
                                      com.google.protobuf.CodedInputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }

    public static Operation parseFrom(
                                      com.google.protobuf.CodedInputStream input,
                                      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @Override
    public Builder newBuilderForType() {
        return newBuilder();
    }

    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }

    public static Builder newBuilder(Operation prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    @Override
    public Builder toBuilder() {
        return this.equals(DEFAULT_INSTANCE)
                ? new Builder()
                : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(BuilderParent parent) {
        return new Builder(parent);
    }

    /**
     * Protobuf type {@code eventmesh.catalog.api.protocol.Operation}
     */
    public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder>
            implements
                OperationOrBuilder {

        private String type = "";
        private String channelName = "";
        private String schema = "";

        public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
            return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_Operation_descriptor;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_Operation_fieldAccessorTable
                    .ensureFieldAccessorsInitialized(
                            Operation.class, Builder.class);
        }

        // Construct using org.apache.eventmesh.common.protocol.catalog.protos.Operation.newBuilder()
        private Builder() {
            maybeForceBuilderInitialization();
        }

        private Builder(
                        BuilderParent parent) {
            super(parent);
            maybeForceBuilderInitialization();
        }

        private void maybeForceBuilderInitialization() {
            if (com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders) {
            }
        }

        @Override
        public Builder clear() {
            super.clear();
            channelName = "";
            schema = "";
            type = "";
            return this;
        }

        @Override
        public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
            return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_Operation_descriptor;
        }

        @Override
        public Operation getDefaultInstanceForType() {
            return Operation.getDefaultInstance();
        }

        @Override
        public Operation build() {
            Operation result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @Override
        public Operation buildPartial() {
            Operation result = new Operation(this);
            result.channelName = channelName;
            result.schema = schema;
            result.type = type;
            onBuilt();
            return result;
        }

        @Override
        public Builder clone() {
            return super.clone();
        }

        @Override
        public Builder setField(
                                com.google.protobuf.Descriptors.FieldDescriptor field,
                                Object value) {
            return super.setField(field, value);
        }

        @Override
        public Builder clearField(
                                  com.google.protobuf.Descriptors.FieldDescriptor field) {
            return super.clearField(field);
        }

        @Override
        public Builder clearOneof(
                                  com.google.protobuf.Descriptors.OneofDescriptor oneof) {
            return super.clearOneof(oneof);
        }

        @Override
        public Builder setRepeatedField(
                                        com.google.protobuf.Descriptors.FieldDescriptor field,
                                        int index, Object value) {
            return super.setRepeatedField(field, index, value);
        }

        @Override
        public Builder addRepeatedField(
                                        com.google.protobuf.Descriptors.FieldDescriptor field,
                                        Object value) {
            return super.addRepeatedField(field, value);
        }

        @Override
        public Builder mergeFrom(com.google.protobuf.Message other) {
            if (other instanceof Operation) {
                return mergeFrom((Operation) other);
            } else {
                super.mergeFrom(other);
                return this;
            }
        }

        public Builder mergeFrom(Operation other) {
            if (other.equals(Operation.getDefaultInstance())) {
                return this;
            }
            if (!other.getChannelName().isEmpty()) {
                channelName = other.channelName;
                onChanged();
            }
            if (!other.getSchema().isEmpty()) {
                schema = other.schema;
                onChanged();
            }
            if (!other.getType().isEmpty()) {
                type = other.type;
                onChanged();
            }
            this.mergeUnknownFields(other.unknownFields);
            onChanged();
            return this;
        }

        @Override
        public final boolean isInitialized() {
            return true;
        }

        @Override
        public Builder mergeFrom(
                                 com.google.protobuf.CodedInputStream input,
                                 com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
            Operation parsedMessage = null;
            try {
                parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                parsedMessage = (Operation) e.getUnfinishedMessage();
                throw e.unwrapIOException();
            } finally {
                if (parsedMessage != null) {
                    mergeFrom(parsedMessage);
                }
            }
            return this;
        }

        /**
         * <code>string channel_name = 1;</code>
         *
         * @return The channelName.
         */
        public String getChannelName() {
            return channelName;
        }

        /**
         * <code>string channel_name = 1;</code>
         *
         * @return The bytes for channelName.
         */
        public com.google.protobuf.ByteString getChannelNameBytes() {

            return ByteString.copyFromUtf8(channelName);
        }

        /**
         * <code>string channel_name = 1;</code>
         *
         * @param value The channelName to set.
         * @return This builder for chaining.
         */
        public Builder setChannelName(String value) {
            Objects.requireNonNull(value, "channelname can not be null");

            channelName = value;
            onChanged();
            return this;
        }

        /**
         * <code>string channel_name = 1;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearChannelName() {
            channelName = getDefaultInstance().getChannelName();
            onChanged();
            return this;
        }

        /**
         * <code>string channel_name = 1;</code>
         *
         * @param value The bytes for channelName to set.
         * @return This builder for chaining.
         */
        public Builder setChannelNameBytes(com.google.protobuf.ByteString value) {
            Objects.requireNonNull(value, "channelname can not be null");
            checkByteStringIsUtf8(value);

            channelName = value.toStringUtf8();
            onChanged();
            return this;
        }

        /**
         * <code>string schema = 2;</code>
         *
         * @return The schema.
         */
        public String getSchema() {
            return schema;
        }

        /**
         * <code>string schema = 2;</code>
         *
         * @return The bytes for schema.
         */
        public com.google.protobuf.ByteString getSchemaBytes() {
            return ByteString.copyFromUtf8(schema);
        }

        /**
         * <code>string schema = 2;</code>
         *
         * @param value The schema to set.
         * @return This builder for chaining.
         */
        public Builder setSchema(String value) {
            Objects.requireNonNull(value, "schema can not be null");

            schema = value;
            onChanged();
            return this;
        }

        /**
         * <code>string schema = 2;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearSchema() {
            schema = getDefaultInstance().getSchema();
            onChanged();
            return this;
        }

        /**
         * <code>string schema = 2;</code>
         *
         * @param value The bytes for schema to set.
         * @return This builder for chaining.
         */
        public Builder setSchemaBytes(com.google.protobuf.ByteString value) {
            Objects.requireNonNull(value, "schema can not be null");
            checkByteStringIsUtf8(value);

            schema = value.toStringUtf8();
            onChanged();
            return this;
        }

        /**
         * <pre>
         * publish/subscribe
         * </pre>
         *
         * <code>string type = 3;</code>
         *
         * @return The type.
         */
        public String getType() {

            return type;
        }

        /**
         * <pre>
         * publish/subscribe
         * </pre>
         *
         * <code>string type = 3;</code>
         *
         * @return The bytes for type.
         */
        public com.google.protobuf.ByteString getTypeBytes() {
            return ByteString.copyFromUtf8(type);
        }

        /**
         * <pre>
         * publish/subscribe
         * </pre>
         *
         * <code>string type = 3;</code>
         *
         * @param value The type to set.
         * @return This builder for chaining.
         */
        public Builder setType(String value) {
            Objects.requireNonNull(value, "type can not be null");

            type = value;
            onChanged();
            return this;
        }

        /**
         * <pre>
         * publish/subscribe
         * </pre>
         *
         * <code>string type = 3;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearType() {
            type = getDefaultInstance().getType();
            onChanged();
            return this;
        }

        /**
         * <pre>
         * publish/subscribe
         * </pre>
         *
         * <code>string type = 3;</code>
         *
         * @param value The bytes for type to set.
         * @return This builder for chaining.
         */
        public Builder setTypeBytes(com.google.protobuf.ByteString value) {
            Objects.requireNonNull(value, "type can not be null");
            checkByteStringIsUtf8(value);

            type = value.toStringUtf8();
            onChanged();
            return this;
        }

        @Override
        public final Builder setUnknownFields(
                                              final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.setUnknownFields(unknownFields);
        }

        @Override
        public final Builder mergeUnknownFields(
                                                final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.mergeUnknownFields(unknownFields);
        }

        // @@protoc_insertion_point(builder_scope:eventmesh.catalog.api.protocol.Operation)
    }

    // @@protoc_insertion_point(class_scope:eventmesh.catalog.api.protocol.Operation)

    private static final com.google.protobuf.Parser<Operation> PARSER = new com.google.protobuf.AbstractParser<Operation>() {

        @Override
        public Operation parsePartialFrom(
                                          com.google.protobuf.CodedInputStream input,
                                          com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
            return new Operation(input, extensionRegistry);
        }
    };

    public static com.google.protobuf.Parser<Operation> parser() {
        return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<Operation> getParserForType() {
        return PARSER;
    }

    @Override
    public Operation getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

}
