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

package org.apache.eventmesh.common.protocol.workflow.protos;

import java.util.Objects;

import com.google.protobuf.ByteString;

/**
 * Protobuf type {@code eventmesh.workflow.api.protocol.ExecuteResponse}
 */
@SuppressWarnings({"all"})
public final class ExecuteResponse extends com.google.protobuf.GeneratedMessageV3 implements ExecuteResponseOrBuilder {

    private static final long serialVersionUID = -6807939196493979674L;

    public static final int INSTANCE_ID_FIELD_NUMBER = 1;
    private volatile String instanceId;

    private byte memoizedIsInitialized = -1;

    // Use ExecuteResponse.newBuilder() to construct.
    private ExecuteResponse(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
        super(builder);
    }

    private ExecuteResponse() {
        instanceId = "";
    }

    @Override
    @SuppressWarnings({"unused"})
    protected Object newInstance(UnusedPrivateParameter unused) {
        return new ExecuteResponse();
    }

    @Override
    public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
        return this.unknownFields;
    }

    private ExecuteResponse(
                            com.google.protobuf.CodedInputStream input,
                            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                                                                                         throws com.google.protobuf.InvalidProtocolBufferException {
        this();
        Objects.requireNonNull(extensionRegistry, "ExtensionRegistryLite can not be null");
        Objects.requireNonNull(input, "CodedInputStream can not be null");

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
                        instanceId = input.readStringRequireUtf8();
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
        return EventmeshWorkflowGrpc.internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor;
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return EventmeshWorkflowGrpc.internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_fieldAccessorTable
                .ensureFieldAccessorsInitialized(ExecuteResponse.class, Builder.class);
    }

    /**
     * <code>string instance_id = 1;</code>
     *
     * @return The instanceId.
     */
    @Override
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * <code>string instance_id = 1;</code>
     *
     * @return The bytes for instanceId.
     */
    @Override
    public com.google.protobuf.ByteString getInstanceIdBytes() {
        return ByteString.copyFromUtf8(instanceId);
    }

    @Override
    public final boolean isInitialized() {
        if (memoizedIsInitialized == 1) {
            return true;
        }

        if (memoizedIsInitialized == 0) {
            return false;
        }

        memoizedIsInitialized = 1;
        return true;
    }

    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
        if (!getInstanceIdBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 1, instanceId);
        }
        unknownFields.writeTo(output);
    }

    @Override
    public int getSerializedSize() {
        int size = memoizedSize;
        if (size != -1)
            return size;

        size = 0;
        if (!getInstanceIdBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, instanceId);
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
        if (!(obj instanceof ExecuteResponse)) {
            return super.equals(obj);
        }
        ExecuteResponse other = (ExecuteResponse) obj;

        if (!getInstanceId()
                .equals(other.getInstanceId()))
            return false;
        if (!unknownFields.equals(other.unknownFields))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        if (memoizedHashCode != 0) {
            return memoizedHashCode;
        }
        int hash = 41;
        hash = (19 * hash) + getDescriptor().hashCode();
        hash = (37 * hash) + INSTANCE_ID_FIELD_NUMBER;
        hash = (53 * hash) + getInstanceId().hashCode();
        hash = (29 * hash) + unknownFields.hashCode();
        memoizedHashCode = hash;
        return hash;
    }

    public static ExecuteResponse parseFrom(
                                            java.nio.ByteBuffer data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static ExecuteResponse parseFrom(
                                            java.nio.ByteBuffer data,
                                            com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static ExecuteResponse parseFrom(
                                            com.google.protobuf.ByteString data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static ExecuteResponse parseFrom(
                                            com.google.protobuf.ByteString data,
                                            com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static ExecuteResponse parseFrom(byte[] data) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static ExecuteResponse parseFrom(
                                            byte[] data,
                                            com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static ExecuteResponse parseFrom(java.io.InputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }

    public static ExecuteResponse parseFrom(
                                            java.io.InputStream input,
                                            com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public static ExecuteResponse parseDelimitedFrom(java.io.InputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input);
    }

    public static ExecuteResponse parseDelimitedFrom(
                                                     java.io.InputStream input,
                                                     com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }

    public static ExecuteResponse parseFrom(
                                            com.google.protobuf.CodedInputStream input) throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }

    public static ExecuteResponse parseFrom(
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

    public static Builder newBuilder(ExecuteResponse prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    @Override
    public Builder toBuilder() {
        return this == DEFAULT_INSTANCE
                ? new Builder()
                : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(
                                        BuilderParent parent) {
        return new Builder(parent);
    }

    /**
     * Protobuf type {@code eventmesh.workflow.api.protocol.ExecuteResponse}
     */
    public static final class Builder
            extends
                com.google.protobuf.GeneratedMessageV3.Builder<Builder>
            implements
                ExecuteResponseOrBuilder {

        public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
            return EventmeshWorkflowGrpc.internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return EventmeshWorkflowGrpc.internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_fieldAccessorTable
                    .ensureFieldAccessorsInitialized(
                            ExecuteResponse.class, Builder.class);
        }

        // Construct using org.apache.eventmesh.common.protocol.workflow.protos.ExecuteResponse.newBuilder()
        private Builder() {
            maybeForceBuilderInitialization();
        }

        private Builder(BuilderParent parent) {
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
            instanceId = "";
            return this;
        }

        @Override
        public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
            return EventmeshWorkflowGrpc.internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor;
        }

        @Override
        public ExecuteResponse getDefaultInstanceForType() {
            return ExecuteResponse.getDefaultInstance();
        }

        @Override
        public ExecuteResponse build() {
            ExecuteResponse result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @Override
        public ExecuteResponse buildPartial() {
            ExecuteResponse result = new ExecuteResponse(this);
            result.instanceId = instanceId;
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
            Objects.requireNonNull(other, "Message can not be null");

            if (other instanceof ExecuteResponse) {
                return mergeFrom((ExecuteResponse) other);
            } else {
                super.mergeFrom(other);
                return this;
            }
        }

        public Builder mergeFrom(ExecuteResponse other) {
            Objects.requireNonNull(other, "ExecuteResponse can not be null");

            if (other == ExecuteResponse.getDefaultInstance())
                return this;
            if (!other.getInstanceId().isEmpty()) {
                instanceId = other.instanceId;
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
            Objects.requireNonNull(input, "CodedInputStream can not be null");
            Objects.requireNonNull(extensionRegistry, "ExtensionRegistryLite can not be null");

            ExecuteResponse parsedMessage = null;
            try {
                parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                parsedMessage = (ExecuteResponse) e.getUnfinishedMessage();
                throw e.unwrapIOException();
            } finally {
                if (parsedMessage != null) {
                    mergeFrom(parsedMessage);
                }
            }

            return this;
        }

        private String instanceId = "";

        /**
         * <code>string instance_id = 1;</code>
         *
         * @return The instanceId.
         */
        public String getInstanceId() {
            return instanceId;
        }

        /**
         * <code>string instance_id = 1;</code>
         *
         * @return The bytes for instanceId.
         */
        public com.google.protobuf.ByteString getInstanceIdBytes() {
            return ByteString.copyFromUtf8(instanceId);
        }

        /**
         * <code>string instance_id = 1;</code>
         *
         * @param value The instanceId to set.
         * @return This builder for chaining.
         */
        public Builder setInstanceId(
                                     String value) {
            Objects.requireNonNull(value, "InstanceId can not be null");
            instanceId = value;
            onChanged();
            return this;
        }

        /**
         * <code>string instance_id = 1;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearInstanceId() {
            instanceId = getDefaultInstance().getInstanceId();
            onChanged();
            return this;
        }

        /**
         * <code>string instance_id = 1;</code>
         *
         * @param value The bytes for instanceId to set.
         * @return This builder for chaining.
         */
        public Builder setInstanceIdBytes(
                                          com.google.protobuf.ByteString value) {
            Objects.requireNonNull(value, "ByteString can not be null");
            checkByteStringIsUtf8(value);
            instanceId = value.toStringUtf8();
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

        // @@protoc_insertion_point(builder_scope:eventmesh.workflow.api.protocol.ExecuteResponse)
    }

    // @@protoc_insertion_point(class_scope:eventmesh.workflow.api.protocol.ExecuteResponse)
    private static final ExecuteResponse DEFAULT_INSTANCE;

    static {
        DEFAULT_INSTANCE = new ExecuteResponse();
    }

    public static ExecuteResponse getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<ExecuteResponse> PARSER =
            new com.google.protobuf.AbstractParser<ExecuteResponse>() {

                @Override
                public ExecuteResponse parsePartialFrom(
                                                        com.google.protobuf.CodedInputStream input,
                                                        com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
                    return new ExecuteResponse(input, extensionRegistry);
                }
            };

    public static com.google.protobuf.Parser<ExecuteResponse> parser() {
        return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<ExecuteResponse> getParserForType() {
        return PARSER;
    }

    @Override
    public ExecuteResponse getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

}
