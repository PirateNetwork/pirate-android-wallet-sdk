// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: WalletData.proto

package rpc;

public final class WalletDataOuterClass {
  private WalletDataOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }
  public interface WalletDataOrBuilder extends
      // @@protoc_insertion_point(interface_extends:rpc.WalletData)
      com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <code>optional string name = 1;</code>
     */
    java.lang.String getName();
    /**
     * <code>optional string name = 1;</code>
     */
    com.google.protobuf.ByteString
        getNameBytes();

    /**
     * <code>optional int32 id = 2;</code>
     */
    int getId();

    /**
     * <code>optional string emails = 3;</code>
     */
    java.lang.String getEmails();
    /**
     * <code>optional string emails = 3;</code>
     */
    com.google.protobuf.ByteString
        getEmailsBytes();
  }
  /**
   * Protobuf type {@code rpc.WalletData}
   */
  public  static final class WalletData extends
      com.google.protobuf.GeneratedMessageLite<
          WalletData, WalletData.Builder> implements
      // @@protoc_insertion_point(message_implements:rpc.WalletData)
      WalletDataOrBuilder {
    private WalletData() {
      name_ = "";
      emails_ = "";
    }
    public static final int NAME_FIELD_NUMBER = 1;
    private java.lang.String name_;
    /**
     * <code>optional string name = 1;</code>
     */
    public java.lang.String getName() {
      return name_;
    }
    /**
     * <code>optional string name = 1;</code>
     */
    public com.google.protobuf.ByteString
        getNameBytes() {
      return com.google.protobuf.ByteString.copyFromUtf8(name_);
    }
    /**
     * <code>optional string name = 1;</code>
     */
    private void setName(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      name_ = value;
    }
    /**
     * <code>optional string name = 1;</code>
     */
    private void clearName() {
      
      name_ = getDefaultInstance().getName();
    }
    /**
     * <code>optional string name = 1;</code>
     */
    private void setNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      name_ = value.toStringUtf8();
    }

    public static final int ID_FIELD_NUMBER = 2;
    private int id_;
    /**
     * <code>optional int32 id = 2;</code>
     */
    public int getId() {
      return id_;
    }
    /**
     * <code>optional int32 id = 2;</code>
     */
    private void setId(int value) {
      
      id_ = value;
    }
    /**
     * <code>optional int32 id = 2;</code>
     */
    private void clearId() {
      
      id_ = 0;
    }

    public static final int EMAILS_FIELD_NUMBER = 3;
    private java.lang.String emails_;
    /**
     * <code>optional string emails = 3;</code>
     */
    public java.lang.String getEmails() {
      return emails_;
    }
    /**
     * <code>optional string emails = 3;</code>
     */
    public com.google.protobuf.ByteString
        getEmailsBytes() {
      return com.google.protobuf.ByteString.copyFromUtf8(emails_);
    }
    /**
     * <code>optional string emails = 3;</code>
     */
    private void setEmails(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      emails_ = value;
    }
    /**
     * <code>optional string emails = 3;</code>
     */
    private void clearEmails() {
      
      emails_ = getDefaultInstance().getEmails();
    }
    /**
     * <code>optional string emails = 3;</code>
     */
    private void setEmailsBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      emails_ = value.toStringUtf8();
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (!name_.isEmpty()) {
        output.writeString(1, getName());
      }
      if (id_ != 0) {
        output.writeInt32(2, id_);
      }
      if (!emails_.isEmpty()) {
        output.writeString(3, getEmails());
      }
    }

    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (!name_.isEmpty()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(1, getName());
      }
      if (id_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(2, id_);
      }
      if (!emails_.isEmpty()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(3, getEmails());
      }
      memoizedSerializedSize = size;
      return size;
    }

    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }
    public static rpc.WalletDataOuterClass.WalletData parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }
    public static rpc.WalletDataOuterClass.WalletData parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static rpc.WalletDataOuterClass.WalletData parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(rpc.WalletDataOuterClass.WalletData prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    /**
     * Protobuf type {@code rpc.WalletData}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          rpc.WalletDataOuterClass.WalletData, Builder> implements
        // @@protoc_insertion_point(builder_implements:rpc.WalletData)
        rpc.WalletDataOuterClass.WalletDataOrBuilder {
      // Construct using rpc.WalletDataOuterClass.WalletData.newBuilder()
      private Builder() {
        super(DEFAULT_INSTANCE);
      }


      /**
       * <code>optional string name = 1;</code>
       */
      public java.lang.String getName() {
        return instance.getName();
      }
      /**
       * <code>optional string name = 1;</code>
       */
      public com.google.protobuf.ByteString
          getNameBytes() {
        return instance.getNameBytes();
      }
      /**
       * <code>optional string name = 1;</code>
       */
      public Builder setName(
          java.lang.String value) {
        copyOnWrite();
        instance.setName(value);
        return this;
      }
      /**
       * <code>optional string name = 1;</code>
       */
      public Builder clearName() {
        copyOnWrite();
        instance.clearName();
        return this;
      }
      /**
       * <code>optional string name = 1;</code>
       */
      public Builder setNameBytes(
          com.google.protobuf.ByteString value) {
        copyOnWrite();
        instance.setNameBytes(value);
        return this;
      }

      /**
       * <code>optional int32 id = 2;</code>
       */
      public int getId() {
        return instance.getId();
      }
      /**
       * <code>optional int32 id = 2;</code>
       */
      public Builder setId(int value) {
        copyOnWrite();
        instance.setId(value);
        return this;
      }
      /**
       * <code>optional int32 id = 2;</code>
       */
      public Builder clearId() {
        copyOnWrite();
        instance.clearId();
        return this;
      }

      /**
       * <code>optional string emails = 3;</code>
       */
      public java.lang.String getEmails() {
        return instance.getEmails();
      }
      /**
       * <code>optional string emails = 3;</code>
       */
      public com.google.protobuf.ByteString
          getEmailsBytes() {
        return instance.getEmailsBytes();
      }
      /**
       * <code>optional string emails = 3;</code>
       */
      public Builder setEmails(
          java.lang.String value) {
        copyOnWrite();
        instance.setEmails(value);
        return this;
      }
      /**
       * <code>optional string emails = 3;</code>
       */
      public Builder clearEmails() {
        copyOnWrite();
        instance.clearEmails();
        return this;
      }
      /**
       * <code>optional string emails = 3;</code>
       */
      public Builder setEmailsBytes(
          com.google.protobuf.ByteString value) {
        copyOnWrite();
        instance.setEmailsBytes(value);
        return this;
      }

      // @@protoc_insertion_point(builder_scope:rpc.WalletData)
    }
    protected final Object dynamicMethod(
        com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
        Object arg0, Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE: {
          return new rpc.WalletDataOuterClass.WalletData();
        }
        case IS_INITIALIZED: {
          return DEFAULT_INSTANCE;
        }
        case MAKE_IMMUTABLE: {
          return null;
        }
        case NEW_BUILDER: {
          return new Builder();
        }
        case VISIT: {
          Visitor visitor = (Visitor) arg0;
          rpc.WalletDataOuterClass.WalletData other = (rpc.WalletDataOuterClass.WalletData) arg1;
          name_ = visitor.visitString(!name_.isEmpty(), name_,
              !other.name_.isEmpty(), other.name_);
          id_ = visitor.visitInt(id_ != 0, id_,
              other.id_ != 0, other.id_);
          emails_ = visitor.visitString(!emails_.isEmpty(), emails_,
              !other.emails_.isEmpty(), other.emails_);
          if (visitor == com.google.protobuf.GeneratedMessageLite.MergeFromVisitor
              .INSTANCE) {
          }
          return this;
        }
        case MERGE_FROM_STREAM: {
          com.google.protobuf.CodedInputStream input =
              (com.google.protobuf.CodedInputStream) arg0;
          com.google.protobuf.ExtensionRegistryLite extensionRegistry =
              (com.google.protobuf.ExtensionRegistryLite) arg1;
          try {
            boolean done = false;
            while (!done) {
              int tag = input.readTag();
              switch (tag) {
                case 0:
                  done = true;
                  break;
                default: {
                  if (!input.skipField(tag)) {
                    done = true;
                  }
                  break;
                }
                case 10: {
                  String s = input.readStringRequireUtf8();

                  name_ = s;
                  break;
                }
                case 16: {

                  id_ = input.readInt32();
                  break;
                }
                case 26: {
                  String s = input.readStringRequireUtf8();

                  emails_ = s;
                  break;
                }
              }
            }
          } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new RuntimeException(e.setUnfinishedMessage(this));
          } catch (java.io.IOException e) {
            throw new RuntimeException(
                new com.google.protobuf.InvalidProtocolBufferException(
                    e.getMessage()).setUnfinishedMessage(this));
          } finally {
          }
        }
        case GET_DEFAULT_INSTANCE: {
          return DEFAULT_INSTANCE;
        }
        case GET_PARSER: {
          if (PARSER == null) {    synchronized (rpc.WalletDataOuterClass.WalletData.class) {
              if (PARSER == null) {
                PARSER = new DefaultInstanceBasedParser(DEFAULT_INSTANCE);
              }
            }
          }
          return PARSER;
        }
      }
      throw new UnsupportedOperationException();
    }


    // @@protoc_insertion_point(class_scope:rpc.WalletData)
    private static final rpc.WalletDataOuterClass.WalletData DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new WalletData();
      DEFAULT_INSTANCE.makeImmutable();
    }

    public static rpc.WalletDataOuterClass.WalletData getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static volatile com.google.protobuf.Parser<WalletData> PARSER;

    public static com.google.protobuf.Parser<WalletData> parser() {
      return DEFAULT_INSTANCE.getParserForType();
    }
  }


  static {
  }

  // @@protoc_insertion_point(outer_class_scope)
}