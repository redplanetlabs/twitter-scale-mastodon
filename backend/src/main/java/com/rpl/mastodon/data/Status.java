/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class Status implements org.apache.thrift.TBase<Status, Status._Fields>, java.io.Serializable, Cloneable, Comparable<Status> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("Status");

  private static final org.apache.thrift.protocol.TField AUTHOR_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("authorId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField CONTENT_FIELD_DESC = new org.apache.thrift.protocol.TField("content", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)3);
  private static final org.apache.thrift.protocol.TField REMOTE_URL_FIELD_DESC = new org.apache.thrift.protocol.TField("remoteUrl", org.apache.thrift.protocol.TType.STRING, (short)4);
  private static final org.apache.thrift.protocol.TField LANGUAGE_FIELD_DESC = new org.apache.thrift.protocol.TField("language", org.apache.thrift.protocol.TType.STRING, (short)5);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new StatusStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new StatusTupleSchemeFactory();

  public long authorId; // required
  public @org.apache.thrift.annotation.Nullable StatusContent content; // required
  public long timestamp; // required
  public @org.apache.thrift.annotation.Nullable java.lang.String remoteUrl; // optional
  public @org.apache.thrift.annotation.Nullable java.lang.String language; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    AUTHOR_ID((short)1, "authorId"),
    CONTENT((short)2, "content"),
    TIMESTAMP((short)3, "timestamp"),
    REMOTE_URL((short)4, "remoteUrl"),
    LANGUAGE((short)5, "language");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // AUTHOR_ID
          return AUTHOR_ID;
        case 2: // CONTENT
          return CONTENT;
        case 3: // TIMESTAMP
          return TIMESTAMP;
        case 4: // REMOTE_URL
          return REMOTE_URL;
        case 5: // LANGUAGE
          return LANGUAGE;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    @Override
    public short getThriftFieldId() {
      return _thriftId;
    }

    @Override
    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __AUTHORID_ISSET_ID = 0;
  private static final int __TIMESTAMP_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.REMOTE_URL,_Fields.LANGUAGE};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.AUTHOR_ID, new org.apache.thrift.meta_data.FieldMetaData("authorId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.CONTENT, new org.apache.thrift.meta_data.FieldMetaData("content", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StatusContent.class)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    tmpMap.put(_Fields.REMOTE_URL, new org.apache.thrift.meta_data.FieldMetaData("remoteUrl", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.LANGUAGE, new org.apache.thrift.meta_data.FieldMetaData("language", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(Status.class, metaDataMap);
  }

  public Status() {
  }

  public Status(
    long authorId,
    StatusContent content,
    long timestamp)
  {
    this();
    this.authorId = authorId;
    setAuthorIdIsSet(true);
    this.content = content;
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public Status(Status other) {
    __isset_bitfield = other.__isset_bitfield;
    this.authorId = other.authorId;
    if (other.isSetContent()) {
      this.content = new StatusContent(other.content);
    }
    this.timestamp = other.timestamp;
    if (other.isSetRemoteUrl()) {
      this.remoteUrl = other.remoteUrl;
    }
    if (other.isSetLanguage()) {
      this.language = other.language;
    }
  }

  @Override
  public Status deepCopy() {
    return new Status(this);
  }

  @Override
  public void clear() {
    setAuthorIdIsSet(false);
    this.authorId = 0;
    this.content = null;
    setTimestampIsSet(false);
    this.timestamp = 0;
    this.remoteUrl = null;
    this.language = null;
  }

  public long getAuthorId() {
    return this.authorId;
  }

  public Status setAuthorId(long authorId) {
    this.authorId = authorId;
    setAuthorIdIsSet(true);
    return this;
  }

  public void unsetAuthorId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __AUTHORID_ISSET_ID);
  }

  /** Returns true if field authorId is set (has been assigned a value) and false otherwise */
  public boolean isSetAuthorId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __AUTHORID_ISSET_ID);
  }

  public void setAuthorIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __AUTHORID_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public StatusContent getContent() {
    return this.content;
  }

  public Status setContent(@org.apache.thrift.annotation.Nullable StatusContent content) {
    this.content = content;
    return this;
  }

  public void unsetContent() {
    this.content = null;
  }

  /** Returns true if field content is set (has been assigned a value) and false otherwise */
  public boolean isSetContent() {
    return this.content != null;
  }

  public void setContentIsSet(boolean value) {
    if (!value) {
      this.content = null;
    }
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public Status setTimestamp(long timestamp) {
    this.timestamp = timestamp;
    setTimestampIsSet(true);
    return this;
  }

  public void unsetTimestamp() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
  }

  /** Returns true if field timestamp is set (has been assigned a value) and false otherwise */
  public boolean isSetTimestamp() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
  }

  public void setTimestampIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __TIMESTAMP_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getRemoteUrl() {
    return this.remoteUrl;
  }

  public Status setRemoteUrl(@org.apache.thrift.annotation.Nullable java.lang.String remoteUrl) {
    this.remoteUrl = remoteUrl;
    return this;
  }

  public void unsetRemoteUrl() {
    this.remoteUrl = null;
  }

  /** Returns true if field remoteUrl is set (has been assigned a value) and false otherwise */
  public boolean isSetRemoteUrl() {
    return this.remoteUrl != null;
  }

  public void setRemoteUrlIsSet(boolean value) {
    if (!value) {
      this.remoteUrl = null;
    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getLanguage() {
    return this.language;
  }

  public Status setLanguage(@org.apache.thrift.annotation.Nullable java.lang.String language) {
    this.language = language;
    return this;
  }

  public void unsetLanguage() {
    this.language = null;
  }

  /** Returns true if field language is set (has been assigned a value) and false otherwise */
  public boolean isSetLanguage() {
    return this.language != null;
  }

  public void setLanguageIsSet(boolean value) {
    if (!value) {
      this.language = null;
    }
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case AUTHOR_ID:
      if (value == null) {
        unsetAuthorId();
      } else {
        setAuthorId((java.lang.Long)value);
      }
      break;

    case CONTENT:
      if (value == null) {
        unsetContent();
      } else {
        setContent((StatusContent)value);
      }
      break;

    case TIMESTAMP:
      if (value == null) {
        unsetTimestamp();
      } else {
        setTimestamp((java.lang.Long)value);
      }
      break;

    case REMOTE_URL:
      if (value == null) {
        unsetRemoteUrl();
      } else {
        setRemoteUrl((java.lang.String)value);
      }
      break;

    case LANGUAGE:
      if (value == null) {
        unsetLanguage();
      } else {
        setLanguage((java.lang.String)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case AUTHOR_ID:
      return getAuthorId();

    case CONTENT:
      return getContent();

    case TIMESTAMP:
      return getTimestamp();

    case REMOTE_URL:
      return getRemoteUrl();

    case LANGUAGE:
      return getLanguage();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  @Override
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case AUTHOR_ID:
      return isSetAuthorId();
    case CONTENT:
      return isSetContent();
    case TIMESTAMP:
      return isSetTimestamp();
    case REMOTE_URL:
      return isSetRemoteUrl();
    case LANGUAGE:
      return isSetLanguage();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof Status)
      return this.equals((Status)that);
    return false;
  }

  public boolean equals(Status that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_authorId = true;
    boolean that_present_authorId = true;
    if (this_present_authorId || that_present_authorId) {
      if (!(this_present_authorId && that_present_authorId))
        return false;
      if (this.authorId != that.authorId)
        return false;
    }

    boolean this_present_content = true && this.isSetContent();
    boolean that_present_content = true && that.isSetContent();
    if (this_present_content || that_present_content) {
      if (!(this_present_content && that_present_content))
        return false;
      if (!this.content.equals(that.content))
        return false;
    }

    boolean this_present_timestamp = true;
    boolean that_present_timestamp = true;
    if (this_present_timestamp || that_present_timestamp) {
      if (!(this_present_timestamp && that_present_timestamp))
        return false;
      if (this.timestamp != that.timestamp)
        return false;
    }

    boolean this_present_remoteUrl = true && this.isSetRemoteUrl();
    boolean that_present_remoteUrl = true && that.isSetRemoteUrl();
    if (this_present_remoteUrl || that_present_remoteUrl) {
      if (!(this_present_remoteUrl && that_present_remoteUrl))
        return false;
      if (!this.remoteUrl.equals(that.remoteUrl))
        return false;
    }

    boolean this_present_language = true && this.isSetLanguage();
    boolean that_present_language = true && that.isSetLanguage();
    if (this_present_language || that_present_language) {
      if (!(this_present_language && that_present_language))
        return false;
      if (!this.language.equals(that.language))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(authorId);

    hashCode = hashCode * 8191 + ((isSetContent()) ? 131071 : 524287);
    if (isSetContent())
      hashCode = hashCode * 8191 + content.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    hashCode = hashCode * 8191 + ((isSetRemoteUrl()) ? 131071 : 524287);
    if (isSetRemoteUrl())
      hashCode = hashCode * 8191 + remoteUrl.hashCode();

    hashCode = hashCode * 8191 + ((isSetLanguage()) ? 131071 : 524287);
    if (isSetLanguage())
      hashCode = hashCode * 8191 + language.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(Status other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetAuthorId(), other.isSetAuthorId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetAuthorId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.authorId, other.authorId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetContent(), other.isSetContent());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetContent()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.content, other.content);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetTimestamp(), other.isSetTimestamp());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTimestamp()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.timestamp, other.timestamp);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetRemoteUrl(), other.isSetRemoteUrl());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetRemoteUrl()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.remoteUrl, other.remoteUrl);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetLanguage(), other.isSetLanguage());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLanguage()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.language, other.language);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  @Override
  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  @Override
  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("Status(");
    boolean first = true;

    sb.append("authorId:");
    sb.append(this.authorId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("content:");
    if (this.content == null) {
      sb.append("null");
    } else {
      sb.append(this.content);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("timestamp:");
    sb.append(this.timestamp);
    first = false;
    if (isSetRemoteUrl()) {
      if (!first) sb.append(", ");
      sb.append("remoteUrl:");
      if (this.remoteUrl == null) {
        sb.append("null");
      } else {
        sb.append(this.remoteUrl);
      }
      first = false;
    }
    if (isSetLanguage()) {
      if (!first) sb.append(", ");
      sb.append("language:");
      if (this.language == null) {
        sb.append("null");
      } else {
        sb.append(this.language);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'authorId' because it's a primitive and you chose the non-beans generator.
    if (content == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'content' was not present! Struct: " + toString());
    }
    // alas, we cannot check 'timestamp' because it's a primitive and you chose the non-beans generator.
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class StatusStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public StatusStandardScheme getScheme() {
      return new StatusStandardScheme();
    }
  }

  private static class StatusStandardScheme extends org.apache.thrift.scheme.StandardScheme<Status> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, Status struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // AUTHOR_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.authorId = iprot.readI64();
              struct.setAuthorIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // CONTENT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.content = new StatusContent();
              struct.content.read(iprot);
              struct.setContentIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // TIMESTAMP
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.timestamp = iprot.readI64();
              struct.setTimestampIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // REMOTE_URL
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.remoteUrl = iprot.readString();
              struct.setRemoteUrlIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // LANGUAGE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.language = iprot.readString();
              struct.setLanguageIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      if (!struct.isSetAuthorId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'authorId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetTimestamp()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'timestamp' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, Status struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(AUTHOR_ID_FIELD_DESC);
      oprot.writeI64(struct.authorId);
      oprot.writeFieldEnd();
      if (struct.content != null) {
        oprot.writeFieldBegin(CONTENT_FIELD_DESC);
        struct.content.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      if (struct.remoteUrl != null) {
        if (struct.isSetRemoteUrl()) {
          oprot.writeFieldBegin(REMOTE_URL_FIELD_DESC);
          oprot.writeString(struct.remoteUrl);
          oprot.writeFieldEnd();
        }
      }
      if (struct.language != null) {
        if (struct.isSetLanguage()) {
          oprot.writeFieldBegin(LANGUAGE_FIELD_DESC);
          oprot.writeString(struct.language);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class StatusTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public StatusTupleScheme getScheme() {
      return new StatusTupleScheme();
    }
  }

  private static class StatusTupleScheme extends org.apache.thrift.scheme.TupleScheme<Status> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, Status struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.authorId);
      struct.content.write(oprot);
      oprot.writeI64(struct.timestamp);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetRemoteUrl()) {
        optionals.set(0);
      }
      if (struct.isSetLanguage()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetRemoteUrl()) {
        oprot.writeString(struct.remoteUrl);
      }
      if (struct.isSetLanguage()) {
        oprot.writeString(struct.language);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, Status struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.authorId = iprot.readI64();
      struct.setAuthorIdIsSet(true);
      struct.content = new StatusContent();
      struct.content.read(iprot);
      struct.setContentIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.remoteUrl = iprot.readString();
        struct.setRemoteUrlIsSet(true);
      }
      if (incoming.get(1)) {
        struct.language = iprot.readString();
        struct.setLanguageIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

