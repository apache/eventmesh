// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: catalog.proto

package org.apache.eventmesh.common.protocol.catalog.protos;

/**
 * Protobuf type {@code eventmesh.catalog.api.protocol.RegistryRequest}
 */
@SuppressWarnings({"all"})
public final class RegistryRequest extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:eventmesh.catalog.api.protocol.RegistryRequest)
    RegistryRequestOrBuilder {
private static final long serialVersionUID = 0L;
  // Use RegistryRequest.newBuilder() to construct.
  private RegistryRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private RegistryRequest() {
    fileName_ = "";
    definition_ = "";
  }

  @Override
  @SuppressWarnings({"unused"})
  protected Object newInstance(
      UnusedPrivateParameter unused) {
    return new RegistryRequest();
  }

  @Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private RegistryRequest(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new NullPointerException();
    }
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
            String s = input.readStringRequireUtf8();

            fileName_ = s;
            break;
          }
          case 18: {
            String s = input.readStringRequireUtf8();

            definition_ = s;
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
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_RegistryRequest_descriptor;
  }

  @Override
  protected FieldAccessorTable
      internalGetFieldAccessorTable() {
    return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_RegistryRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            RegistryRequest.class, Builder.class);
  }

  public static final int FILE_NAME_FIELD_NUMBER = 1;
  private volatile Object fileName_;
  /**
   * <code>string file_name = 1;</code>
   * @return The fileName.
   */
  @Override
  public String getFileName() {
    Object ref = fileName_;
    if (ref instanceof String) {
      return (String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      String s = bs.toStringUtf8();
      fileName_ = s;
      return s;
    }
  }
  /**
   * <code>string file_name = 1;</code>
   * @return The bytes for fileName.
   */
  @Override
  public com.google.protobuf.ByteString
      getFileNameBytes() {
    Object ref = fileName_;
    if (ref instanceof String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (String) ref);
      fileName_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int DEFINITION_FIELD_NUMBER = 2;
  private volatile Object definition_;
  /**
   * <code>string definition = 2;</code>
   * @return The definition.
   */
  @Override
  public String getDefinition() {
    Object ref = definition_;
    if (ref instanceof String) {
      return (String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      String s = bs.toStringUtf8();
      definition_ = s;
      return s;
    }
  }
  /**
   * <code>string definition = 2;</code>
   * @return The bytes for definition.
   */
  @Override
  public com.google.protobuf.ByteString
      getDefinitionBytes() {
    Object ref = definition_;
    if (ref instanceof String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (String) ref);
      definition_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
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
    if (!getFileNameBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, fileName_);
    }
    if (!getDefinitionBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, definition_);
    }
    unknownFields.writeTo(output);
  }

  @Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getFileNameBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, fileName_);
    }
    if (!getDefinitionBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, definition_);
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
    if (!(obj instanceof RegistryRequest)) {
      return super.equals(obj);
    }
    RegistryRequest other = (RegistryRequest) obj;

    if (!getFileName()
        .equals(other.getFileName())) return false;
    if (!getDefinition()
        .equals(other.getDefinition())) return false;
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
    hash = (37 * hash) + FILE_NAME_FIELD_NUMBER;
    hash = (53 * hash) + getFileName().hashCode();
    hash = (37 * hash) + DEFINITION_FIELD_NUMBER;
    hash = (53 * hash) + getDefinition().hashCode();
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static RegistryRequest parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static RegistryRequest parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static RegistryRequest parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static RegistryRequest parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static RegistryRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static RegistryRequest parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static RegistryRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static RegistryRequest parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static RegistryRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static RegistryRequest parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static RegistryRequest parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static RegistryRequest parseFrom(
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
  public static Builder newBuilder(RegistryRequest prototype) {
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
   * Protobuf type {@code eventmesh.catalog.api.protocol.RegistryRequest}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:eventmesh.catalog.api.protocol.RegistryRequest)
      RegistryRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_RegistryRequest_descriptor;
    }

    @Override
    protected FieldAccessorTable
        internalGetFieldAccessorTable() {
      return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_RegistryRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              RegistryRequest.class, Builder.class);
    }

    // Construct using org.apache.eventmesh.common.protocol.catalog.protos.RegistryRequest.newBuilder()
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
      }
    }
    @Override
    public Builder clear() {
      super.clear();
      fileName_ = "";

      definition_ = "";

      return this;
    }

    @Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return EventmeshCatalogGrpc.internal_static_eventmesh_catalog_api_protocol_RegistryRequest_descriptor;
    }

    @Override
    public RegistryRequest getDefaultInstanceForType() {
      return RegistryRequest.getDefaultInstance();
    }

    @Override
    public RegistryRequest build() {
      RegistryRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @Override
    public RegistryRequest buildPartial() {
      RegistryRequest result = new RegistryRequest(this);
      result.fileName_ = fileName_;
      result.definition_ = definition_;
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
      if (other instanceof RegistryRequest) {
        return mergeFrom((RegistryRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(RegistryRequest other) {
      if (other == RegistryRequest.getDefaultInstance()) return this;
      if (!other.getFileName().isEmpty()) {
        fileName_ = other.fileName_;
        onChanged();
      }
      if (!other.getDefinition().isEmpty()) {
        definition_ = other.definition_;
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
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      RegistryRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (RegistryRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private Object fileName_ = "";
    /**
     * <code>string file_name = 1;</code>
     * @return The fileName.
     */
    public String getFileName() {
      Object ref = fileName_;
      if (!(ref instanceof String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        fileName_ = s;
        return s;
      } else {
        return (String) ref;
      }
    }
    /**
     * <code>string file_name = 1;</code>
     * @return The bytes for fileName.
     */
    public com.google.protobuf.ByteString
        getFileNameBytes() {
      Object ref = fileName_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        fileName_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>string file_name = 1;</code>
     * @param value The fileName to set.
     * @return This builder for chaining.
     */
    public Builder setFileName(
        String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      fileName_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>string file_name = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearFileName() {
      
      fileName_ = getDefaultInstance().getFileName();
      onChanged();
      return this;
    }
    /**
     * <code>string file_name = 1;</code>
     * @param value The bytes for fileName to set.
     * @return This builder for chaining.
     */
    public Builder setFileNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      fileName_ = value;
      onChanged();
      return this;
    }

    private Object definition_ = "";
    /**
     * <code>string definition = 2;</code>
     * @return The definition.
     */
    public String getDefinition() {
      Object ref = definition_;
      if (!(ref instanceof String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        definition_ = s;
        return s;
      } else {
        return (String) ref;
      }
    }
    /**
     * <code>string definition = 2;</code>
     * @return The bytes for definition.
     */
    public com.google.protobuf.ByteString
        getDefinitionBytes() {
      Object ref = definition_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        definition_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>string definition = 2;</code>
     * @param value The definition to set.
     * @return This builder for chaining.
     */
    public Builder setDefinition(
        String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      definition_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>string definition = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearDefinition() {
      
      definition_ = getDefaultInstance().getDefinition();
      onChanged();
      return this;
    }
    /**
     * <code>string definition = 2;</code>
     * @param value The bytes for definition to set.
     * @return This builder for chaining.
     */
    public Builder setDefinitionBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      definition_ = value;
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


    // @@protoc_insertion_point(builder_scope:eventmesh.catalog.api.protocol.RegistryRequest)
  }

  // @@protoc_insertion_point(class_scope:eventmesh.catalog.api.protocol.RegistryRequest)
  private static final RegistryRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new RegistryRequest();
  }

  public static RegistryRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<RegistryRequest>
      PARSER = new com.google.protobuf.AbstractParser<RegistryRequest>() {
    @Override
    public RegistryRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new RegistryRequest(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<RegistryRequest> parser() {
    return PARSER;
  }

  @Override
  public com.google.protobuf.Parser<RegistryRequest> getParserForType() {
    return PARSER;
  }

  @Override
  public RegistryRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

