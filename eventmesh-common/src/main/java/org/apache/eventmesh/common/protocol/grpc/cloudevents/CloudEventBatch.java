// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: eventmesh-cloudevents.proto

package org.apache.eventmesh.common.protocol.grpc.cloudevents;

/**
 * Protobuf type {@code org.apache.eventmesh.cloudevents.v1.CloudEventBatch}
 */
public final class CloudEventBatch extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:org.apache.eventmesh.cloudevents.v1.CloudEventBatch)
    CloudEventBatchOrBuilder {
private static final long serialVersionUID = 0L;
  // Use CloudEventBatch.newBuilder() to construct.
  private CloudEventBatch(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private CloudEventBatch() {
    events_ = java.util.Collections.emptyList();
  }

  @Override
  @SuppressWarnings({"unused"})
  protected Object newInstance(
      UnusedPrivateParameter unused) {
    return new CloudEventBatch();
  }

  @Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private CloudEventBatch(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new NullPointerException();
    }
    int mutable_bitField0_ = 0;
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
            if (!((mutable_bitField0_ & 0x00000001) != 0)) {
              events_ = new java.util.ArrayList<CloudEvent>();
              mutable_bitField0_ |= 0x00000001;
            }
            events_.add(
                input.readMessage(CloudEvent.parser(), extensionRegistry));
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
    } catch (com.google.protobuf.UninitializedMessageException e) {
      throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      if (((mutable_bitField0_ & 0x00000001) != 0)) {
        events_ = java.util.Collections.unmodifiableList(events_);
      }
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return EventMeshCloudEvents.internal_static_org_apache_eventmesh_cloudevents_v1_CloudEventBatch_descriptor;
  }

  @Override
  protected FieldAccessorTable
      internalGetFieldAccessorTable() {
    return EventMeshCloudEvents.internal_static_org_apache_eventmesh_cloudevents_v1_CloudEventBatch_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            CloudEventBatch.class, Builder.class);
  }

  public static final int EVENTS_FIELD_NUMBER = 1;
  private java.util.List<CloudEvent> events_;
  /**
   * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
   */
  @Override
  public java.util.List<CloudEvent> getEventsList() {
    return events_;
  }
  /**
   * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
   */
  @Override
  public java.util.List<? extends CloudEventOrBuilder>
      getEventsOrBuilderList() {
    return events_;
  }
  /**
   * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
   */
  @Override
  public int getEventsCount() {
    return events_.size();
  }
  /**
   * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
   */
  @Override
  public CloudEvent getEvents(int index) {
    return events_.get(index);
  }
  /**
   * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
   */
  @Override
  public CloudEventOrBuilder getEventsOrBuilder(
      int index) {
    return events_.get(index);
  }

  private byte memoizedIsInitialized = -1;
  @Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    for (int i = 0; i < events_.size(); i++) {
      output.writeMessage(1, events_.get(i));
    }
    unknownFields.writeTo(output);
  }

  @Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    for (int i = 0; i < events_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, events_.get(i));
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
    if (!(obj instanceof CloudEventBatch)) {
      return super.equals(obj);
    }
    CloudEventBatch other = (CloudEventBatch) obj;

    if (!getEventsList()
        .equals(other.getEventsList())) return false;
    if (!unknownFields.equals(other.unknownFields)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (getEventsCount() > 0) {
      hash = (37 * hash) + EVENTS_FIELD_NUMBER;
      hash = (53 * hash) + getEventsList().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static CloudEventBatch parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static CloudEventBatch parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static CloudEventBatch parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static CloudEventBatch parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static CloudEventBatch parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static CloudEventBatch parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static CloudEventBatch parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static CloudEventBatch parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static CloudEventBatch parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static CloudEventBatch parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static CloudEventBatch parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static CloudEventBatch parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(CloudEventBatch prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @Override
  protected Builder newBuilderForType(
      BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code org.apache.eventmesh.cloudevents.v1.CloudEventBatch}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:org.apache.eventmesh.cloudevents.v1.CloudEventBatch)
      CloudEventBatchOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return EventMeshCloudEvents.internal_static_org_apache_eventmesh_cloudevents_v1_CloudEventBatch_descriptor;
    }

    @Override
    protected FieldAccessorTable
        internalGetFieldAccessorTable() {
      return EventMeshCloudEvents.internal_static_org_apache_eventmesh_cloudevents_v1_CloudEventBatch_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              CloudEventBatch.class, Builder.class);
    }

    // Construct using org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
        getEventsFieldBuilder();
      }
    }
    @Override
    public Builder clear() {
      super.clear();
      if (eventsBuilder_ == null) {
        events_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
      } else {
        eventsBuilder_.clear();
      }
      return this;
    }

    @Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return EventMeshCloudEvents.internal_static_org_apache_eventmesh_cloudevents_v1_CloudEventBatch_descriptor;
    }

    @Override
    public CloudEventBatch getDefaultInstanceForType() {
      return CloudEventBatch.getDefaultInstance();
    }

    @Override
    public CloudEventBatch build() {
      CloudEventBatch result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @Override
    public CloudEventBatch buildPartial() {
      CloudEventBatch result = new CloudEventBatch(this);
      int from_bitField0_ = bitField0_;
      if (eventsBuilder_ == null) {
        if (((bitField0_ & 0x00000001) != 0)) {
          events_ = java.util.Collections.unmodifiableList(events_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.events_ = events_;
      } else {
        result.events_ = eventsBuilder_.build();
      }
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
      if (other instanceof CloudEventBatch) {
        return mergeFrom((CloudEventBatch)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(CloudEventBatch other) {
      if (other == CloudEventBatch.getDefaultInstance()) return this;
      if (eventsBuilder_ == null) {
        if (!other.events_.isEmpty()) {
          if (events_.isEmpty()) {
            events_ = other.events_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureEventsIsMutable();
            events_.addAll(other.events_);
          }
          onChanged();
        }
      } else {
        if (!other.events_.isEmpty()) {
          if (eventsBuilder_.isEmpty()) {
            eventsBuilder_.dispose();
            eventsBuilder_ = null;
            events_ = other.events_;
            bitField0_ = (bitField0_ & ~0x00000001);
            eventsBuilder_ =
              com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders ?
                 getEventsFieldBuilder() : null;
          } else {
            eventsBuilder_.addAllMessages(other.events_);
          }
        }
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
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      CloudEventBatch parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (CloudEventBatch) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private java.util.List<CloudEvent> events_ =
      java.util.Collections.emptyList();
    private void ensureEventsIsMutable() {
      if (!((bitField0_ & 0x00000001) != 0)) {
        events_ = new java.util.ArrayList<CloudEvent>(events_);
        bitField0_ |= 0x00000001;
       }
    }

    private com.google.protobuf.RepeatedFieldBuilderV3<
        CloudEvent, CloudEvent.Builder, CloudEventOrBuilder> eventsBuilder_;

    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public java.util.List<CloudEvent> getEventsList() {
      if (eventsBuilder_ == null) {
        return java.util.Collections.unmodifiableList(events_);
      } else {
        return eventsBuilder_.getMessageList();
      }
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public int getEventsCount() {
      if (eventsBuilder_ == null) {
        return events_.size();
      } else {
        return eventsBuilder_.getCount();
      }
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public CloudEvent getEvents(int index) {
      if (eventsBuilder_ == null) {
        return events_.get(index);
      } else {
        return eventsBuilder_.getMessage(index);
      }
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder setEvents(
        int index, CloudEvent value) {
      if (eventsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventsIsMutable();
        events_.set(index, value);
        onChanged();
      } else {
        eventsBuilder_.setMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder setEvents(
        int index, CloudEvent.Builder builderForValue) {
      if (eventsBuilder_ == null) {
        ensureEventsIsMutable();
        events_.set(index, builderForValue.build());
        onChanged();
      } else {
        eventsBuilder_.setMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder addEvents(CloudEvent value) {
      if (eventsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventsIsMutable();
        events_.add(value);
        onChanged();
      } else {
        eventsBuilder_.addMessage(value);
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder addEvents(
        int index, CloudEvent value) {
      if (eventsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventsIsMutable();
        events_.add(index, value);
        onChanged();
      } else {
        eventsBuilder_.addMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder addEvents(
        CloudEvent.Builder builderForValue) {
      if (eventsBuilder_ == null) {
        ensureEventsIsMutable();
        events_.add(builderForValue.build());
        onChanged();
      } else {
        eventsBuilder_.addMessage(builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder addEvents(
        int index, CloudEvent.Builder builderForValue) {
      if (eventsBuilder_ == null) {
        ensureEventsIsMutable();
        events_.add(index, builderForValue.build());
        onChanged();
      } else {
        eventsBuilder_.addMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder addAllEvents(
        Iterable<? extends CloudEvent> values) {
      if (eventsBuilder_ == null) {
        ensureEventsIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, events_);
        onChanged();
      } else {
        eventsBuilder_.addAllMessages(values);
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder clearEvents() {
      if (eventsBuilder_ == null) {
        events_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
      } else {
        eventsBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public Builder removeEvents(int index) {
      if (eventsBuilder_ == null) {
        ensureEventsIsMutable();
        events_.remove(index);
        onChanged();
      } else {
        eventsBuilder_.remove(index);
      }
      return this;
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public CloudEvent.Builder getEventsBuilder(
        int index) {
      return getEventsFieldBuilder().getBuilder(index);
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public CloudEventOrBuilder getEventsOrBuilder(
        int index) {
      if (eventsBuilder_ == null) {
        return events_.get(index);  } else {
        return eventsBuilder_.getMessageOrBuilder(index);
      }
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public java.util.List<? extends CloudEventOrBuilder>
         getEventsOrBuilderList() {
      if (eventsBuilder_ != null) {
        return eventsBuilder_.getMessageOrBuilderList();
      } else {
        return java.util.Collections.unmodifiableList(events_);
      }
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public CloudEvent.Builder addEventsBuilder() {
      return getEventsFieldBuilder().addBuilder(
          CloudEvent.getDefaultInstance());
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public CloudEvent.Builder addEventsBuilder(
        int index) {
      return getEventsFieldBuilder().addBuilder(
          index, CloudEvent.getDefaultInstance());
    }
    /**
     * <code>repeated .org.apache.eventmesh.cloudevents.v1.CloudEvent events = 1;</code>
     */
    public java.util.List<CloudEvent.Builder>
         getEventsBuilderList() {
      return getEventsFieldBuilder().getBuilderList();
    }
    private com.google.protobuf.RepeatedFieldBuilderV3<
        CloudEvent, CloudEvent.Builder, CloudEventOrBuilder>
        getEventsFieldBuilder() {
      if (eventsBuilder_ == null) {
        eventsBuilder_ = new com.google.protobuf.RepeatedFieldBuilderV3<
            CloudEvent, CloudEvent.Builder, CloudEventOrBuilder>(
                events_,
                ((bitField0_ & 0x00000001) != 0),
                getParentForChildren(),
                isClean());
        events_ = null;
      }
      return eventsBuilder_;
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


    // @@protoc_insertion_point(builder_scope:org.apache.eventmesh.cloudevents.v1.CloudEventBatch)
  }

  // @@protoc_insertion_point(class_scope:org.apache.eventmesh.cloudevents.v1.CloudEventBatch)
  private static final CloudEventBatch DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new CloudEventBatch();
  }

  public static CloudEventBatch getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<CloudEventBatch>
      PARSER = new com.google.protobuf.AbstractParser<CloudEventBatch>() {
    @Override
    public CloudEventBatch parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new CloudEventBatch(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<CloudEventBatch> parser() {
    return PARSER;
  }

  @Override
  public com.google.protobuf.Parser<CloudEventBatch> getParserForType() {
    return PARSER;
  }

  @Override
  public CloudEventBatch getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

