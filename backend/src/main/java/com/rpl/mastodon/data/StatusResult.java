/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class StatusResult implements org.apache.thrift.TBase<StatusResult, StatusResult._Fields>, java.io.Serializable, Cloneable, Comparable<StatusResult> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("StatusResult");

  private static final org.apache.thrift.protocol.TField AUTHOR_FIELD_DESC = new org.apache.thrift.protocol.TField("author", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField CONTENT_FIELD_DESC = new org.apache.thrift.protocol.TField("content", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField METADATA_FIELD_DESC = new org.apache.thrift.protocol.TField("metadata", org.apache.thrift.protocol.TType.STRUCT, (short)3);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)4);
  private static final org.apache.thrift.protocol.TField EDIT_TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("editTimestamp", org.apache.thrift.protocol.TType.I64, (short)5);
  private static final org.apache.thrift.protocol.TField POLL_INFO_FIELD_DESC = new org.apache.thrift.protocol.TField("pollInfo", org.apache.thrift.protocol.TType.STRUCT, (short)6);
  private static final org.apache.thrift.protocol.TField REMOTE_URL_FIELD_DESC = new org.apache.thrift.protocol.TField("remoteUrl", org.apache.thrift.protocol.TType.STRING, (short)7);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new StatusResultStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new StatusResultTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable AccountWithId author; // required
  public @org.apache.thrift.annotation.Nullable StatusResultContent content; // required
  public @org.apache.thrift.annotation.Nullable StatusMetadata metadata; // required
  public long timestamp; // required
  public long editTimestamp; // optional
  public @org.apache.thrift.annotation.Nullable PollInfo pollInfo; // optional
  public @org.apache.thrift.annotation.Nullable java.lang.String remoteUrl; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    AUTHOR((short)1, "author"),
    CONTENT((short)2, "content"),
    METADATA((short)3, "metadata"),
    TIMESTAMP((short)4, "timestamp"),
    EDIT_TIMESTAMP((short)5, "editTimestamp"),
    POLL_INFO((short)6, "pollInfo"),
    REMOTE_URL((short)7, "remoteUrl");

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
        case 1: // AUTHOR
          return AUTHOR;
        case 2: // CONTENT
          return CONTENT;
        case 3: // METADATA
          return METADATA;
        case 4: // TIMESTAMP
          return TIMESTAMP;
        case 5: // EDIT_TIMESTAMP
          return EDIT_TIMESTAMP;
        case 6: // POLL_INFO
          return POLL_INFO;
        case 7: // REMOTE_URL
          return REMOTE_URL;
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
  private static final int __TIMESTAMP_ISSET_ID = 0;
  private static final int __EDITTIMESTAMP_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.EDIT_TIMESTAMP,_Fields.POLL_INFO,_Fields.REMOTE_URL};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.AUTHOR, new org.apache.thrift.meta_data.FieldMetaData("author", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, AccountWithId.class)));
    tmpMap.put(_Fields.CONTENT, new org.apache.thrift.meta_data.FieldMetaData("content", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StatusResultContent.class)));
    tmpMap.put(_Fields.METADATA, new org.apache.thrift.meta_data.FieldMetaData("metadata", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StatusMetadata.class)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    tmpMap.put(_Fields.EDIT_TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("editTimestamp", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    tmpMap.put(_Fields.POLL_INFO, new org.apache.thrift.meta_data.FieldMetaData("pollInfo", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, PollInfo.class)));
    tmpMap.put(_Fields.REMOTE_URL, new org.apache.thrift.meta_data.FieldMetaData("remoteUrl", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(StatusResult.class, metaDataMap);
  }

  public StatusResult() {
  }

  public StatusResult(
    AccountWithId author,
    StatusResultContent content,
    StatusMetadata metadata,
    long timestamp)
  {
    this();
    this.author = author;
    this.content = content;
    this.metadata = metadata;
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public StatusResult(StatusResult other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetAuthor()) {
      this.author = new AccountWithId(other.author);
    }
    if (other.isSetContent()) {
      this.content = new StatusResultContent(other.content);
    }
    if (other.isSetMetadata()) {
      this.metadata = new StatusMetadata(other.metadata);
    }
    this.timestamp = other.timestamp;
    this.editTimestamp = other.editTimestamp;
    if (other.isSetPollInfo()) {
      this.pollInfo = new PollInfo(other.pollInfo);
    }
    if (other.isSetRemoteUrl()) {
      this.remoteUrl = other.remoteUrl;
    }
  }

  @Override
  public StatusResult deepCopy() {
    return new StatusResult(this);
  }

  @Override
  public void clear() {
    this.author = null;
    this.content = null;
    this.metadata = null;
    setTimestampIsSet(false);
    this.timestamp = 0;
    setEditTimestampIsSet(false);
    this.editTimestamp = 0;
    this.pollInfo = null;
    this.remoteUrl = null;
  }

  @org.apache.thrift.annotation.Nullable
  public AccountWithId getAuthor() {
    return this.author;
  }

  public StatusResult setAuthor(@org.apache.thrift.annotation.Nullable AccountWithId author) {
    this.author = author;
    return this;
  }

  public void unsetAuthor() {
    this.author = null;
  }

  /** Returns true if field author is set (has been assigned a value) and false otherwise */
  public boolean isSetAuthor() {
    return this.author != null;
  }

  public void setAuthorIsSet(boolean value) {
    if (!value) {
      this.author = null;
    }
  }

  @org.apache.thrift.annotation.Nullable
  public StatusResultContent getContent() {
    return this.content;
  }

  public StatusResult setContent(@org.apache.thrift.annotation.Nullable StatusResultContent content) {
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

  @org.apache.thrift.annotation.Nullable
  public StatusMetadata getMetadata() {
    return this.metadata;
  }

  public StatusResult setMetadata(@org.apache.thrift.annotation.Nullable StatusMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public void unsetMetadata() {
    this.metadata = null;
  }

  /** Returns true if field metadata is set (has been assigned a value) and false otherwise */
  public boolean isSetMetadata() {
    return this.metadata != null;
  }

  public void setMetadataIsSet(boolean value) {
    if (!value) {
      this.metadata = null;
    }
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public StatusResult setTimestamp(long timestamp) {
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

  public long getEditTimestamp() {
    return this.editTimestamp;
  }

  public StatusResult setEditTimestamp(long editTimestamp) {
    this.editTimestamp = editTimestamp;
    setEditTimestampIsSet(true);
    return this;
  }

  public void unsetEditTimestamp() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __EDITTIMESTAMP_ISSET_ID);
  }

  /** Returns true if field editTimestamp is set (has been assigned a value) and false otherwise */
  public boolean isSetEditTimestamp() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __EDITTIMESTAMP_ISSET_ID);
  }

  public void setEditTimestampIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __EDITTIMESTAMP_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public PollInfo getPollInfo() {
    return this.pollInfo;
  }

  public StatusResult setPollInfo(@org.apache.thrift.annotation.Nullable PollInfo pollInfo) {
    this.pollInfo = pollInfo;
    return this;
  }

  public void unsetPollInfo() {
    this.pollInfo = null;
  }

  /** Returns true if field pollInfo is set (has been assigned a value) and false otherwise */
  public boolean isSetPollInfo() {
    return this.pollInfo != null;
  }

  public void setPollInfoIsSet(boolean value) {
    if (!value) {
      this.pollInfo = null;
    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getRemoteUrl() {
    return this.remoteUrl;
  }

  public StatusResult setRemoteUrl(@org.apache.thrift.annotation.Nullable java.lang.String remoteUrl) {
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

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case AUTHOR:
      if (value == null) {
        unsetAuthor();
      } else {
        setAuthor((AccountWithId)value);
      }
      break;

    case CONTENT:
      if (value == null) {
        unsetContent();
      } else {
        setContent((StatusResultContent)value);
      }
      break;

    case METADATA:
      if (value == null) {
        unsetMetadata();
      } else {
        setMetadata((StatusMetadata)value);
      }
      break;

    case TIMESTAMP:
      if (value == null) {
        unsetTimestamp();
      } else {
        setTimestamp((java.lang.Long)value);
      }
      break;

    case EDIT_TIMESTAMP:
      if (value == null) {
        unsetEditTimestamp();
      } else {
        setEditTimestamp((java.lang.Long)value);
      }
      break;

    case POLL_INFO:
      if (value == null) {
        unsetPollInfo();
      } else {
        setPollInfo((PollInfo)value);
      }
      break;

    case REMOTE_URL:
      if (value == null) {
        unsetRemoteUrl();
      } else {
        setRemoteUrl((java.lang.String)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case AUTHOR:
      return getAuthor();

    case CONTENT:
      return getContent();

    case METADATA:
      return getMetadata();

    case TIMESTAMP:
      return getTimestamp();

    case EDIT_TIMESTAMP:
      return getEditTimestamp();

    case POLL_INFO:
      return getPollInfo();

    case REMOTE_URL:
      return getRemoteUrl();

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
    case AUTHOR:
      return isSetAuthor();
    case CONTENT:
      return isSetContent();
    case METADATA:
      return isSetMetadata();
    case TIMESTAMP:
      return isSetTimestamp();
    case EDIT_TIMESTAMP:
      return isSetEditTimestamp();
    case POLL_INFO:
      return isSetPollInfo();
    case REMOTE_URL:
      return isSetRemoteUrl();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof StatusResult)
      return this.equals((StatusResult)that);
    return false;
  }

  public boolean equals(StatusResult that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_author = true && this.isSetAuthor();
    boolean that_present_author = true && that.isSetAuthor();
    if (this_present_author || that_present_author) {
      if (!(this_present_author && that_present_author))
        return false;
      if (!this.author.equals(that.author))
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

    boolean this_present_metadata = true && this.isSetMetadata();
    boolean that_present_metadata = true && that.isSetMetadata();
    if (this_present_metadata || that_present_metadata) {
      if (!(this_present_metadata && that_present_metadata))
        return false;
      if (!this.metadata.equals(that.metadata))
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

    boolean this_present_editTimestamp = true && this.isSetEditTimestamp();
    boolean that_present_editTimestamp = true && that.isSetEditTimestamp();
    if (this_present_editTimestamp || that_present_editTimestamp) {
      if (!(this_present_editTimestamp && that_present_editTimestamp))
        return false;
      if (this.editTimestamp != that.editTimestamp)
        return false;
    }

    boolean this_present_pollInfo = true && this.isSetPollInfo();
    boolean that_present_pollInfo = true && that.isSetPollInfo();
    if (this_present_pollInfo || that_present_pollInfo) {
      if (!(this_present_pollInfo && that_present_pollInfo))
        return false;
      if (!this.pollInfo.equals(that.pollInfo))
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

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetAuthor()) ? 131071 : 524287);
    if (isSetAuthor())
      hashCode = hashCode * 8191 + author.hashCode();

    hashCode = hashCode * 8191 + ((isSetContent()) ? 131071 : 524287);
    if (isSetContent())
      hashCode = hashCode * 8191 + content.hashCode();

    hashCode = hashCode * 8191 + ((isSetMetadata()) ? 131071 : 524287);
    if (isSetMetadata())
      hashCode = hashCode * 8191 + metadata.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    hashCode = hashCode * 8191 + ((isSetEditTimestamp()) ? 131071 : 524287);
    if (isSetEditTimestamp())
      hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(editTimestamp);

    hashCode = hashCode * 8191 + ((isSetPollInfo()) ? 131071 : 524287);
    if (isSetPollInfo())
      hashCode = hashCode * 8191 + pollInfo.hashCode();

    hashCode = hashCode * 8191 + ((isSetRemoteUrl()) ? 131071 : 524287);
    if (isSetRemoteUrl())
      hashCode = hashCode * 8191 + remoteUrl.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(StatusResult other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetAuthor(), other.isSetAuthor());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetAuthor()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.author, other.author);
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
    lastComparison = java.lang.Boolean.compare(isSetMetadata(), other.isSetMetadata());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMetadata()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.metadata, other.metadata);
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
    lastComparison = java.lang.Boolean.compare(isSetEditTimestamp(), other.isSetEditTimestamp());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetEditTimestamp()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.editTimestamp, other.editTimestamp);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetPollInfo(), other.isSetPollInfo());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetPollInfo()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.pollInfo, other.pollInfo);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("StatusResult(");
    boolean first = true;

    sb.append("author:");
    if (this.author == null) {
      sb.append("null");
    } else {
      sb.append(this.author);
    }
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
    sb.append("metadata:");
    if (this.metadata == null) {
      sb.append("null");
    } else {
      sb.append(this.metadata);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("timestamp:");
    sb.append(this.timestamp);
    first = false;
    if (isSetEditTimestamp()) {
      if (!first) sb.append(", ");
      sb.append("editTimestamp:");
      sb.append(this.editTimestamp);
      first = false;
    }
    if (isSetPollInfo()) {
      if (!first) sb.append(", ");
      sb.append("pollInfo:");
      if (this.pollInfo == null) {
        sb.append("null");
      } else {
        sb.append(this.pollInfo);
      }
      first = false;
    }
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
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (author == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'author' was not present! Struct: " + toString());
    }
    if (content == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'content' was not present! Struct: " + toString());
    }
    if (metadata == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'metadata' was not present! Struct: " + toString());
    }
    // alas, we cannot check 'timestamp' because it's a primitive and you chose the non-beans generator.
    // check for sub-struct validity
    if (author != null) {
      author.validate();
    }
    if (metadata != null) {
      metadata.validate();
    }
    if (pollInfo != null) {
      pollInfo.validate();
    }
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

  private static class StatusResultStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public StatusResultStandardScheme getScheme() {
      return new StatusResultStandardScheme();
    }
  }

  private static class StatusResultStandardScheme extends org.apache.thrift.scheme.StandardScheme<StatusResult> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, StatusResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // AUTHOR
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.author = new AccountWithId();
              struct.author.read(iprot);
              struct.setAuthorIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // CONTENT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.content = new StatusResultContent();
              struct.content.read(iprot);
              struct.setContentIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // METADATA
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.metadata = new StatusMetadata();
              struct.metadata.read(iprot);
              struct.setMetadataIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // TIMESTAMP
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.timestamp = iprot.readI64();
              struct.setTimestampIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // EDIT_TIMESTAMP
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.editTimestamp = iprot.readI64();
              struct.setEditTimestampIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 6: // POLL_INFO
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.pollInfo = new PollInfo();
              struct.pollInfo.read(iprot);
              struct.setPollInfoIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 7: // REMOTE_URL
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.remoteUrl = iprot.readString();
              struct.setRemoteUrlIsSet(true);
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
      if (!struct.isSetTimestamp()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'timestamp' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, StatusResult struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.author != null) {
        oprot.writeFieldBegin(AUTHOR_FIELD_DESC);
        struct.author.write(oprot);
        oprot.writeFieldEnd();
      }
      if (struct.content != null) {
        oprot.writeFieldBegin(CONTENT_FIELD_DESC);
        struct.content.write(oprot);
        oprot.writeFieldEnd();
      }
      if (struct.metadata != null) {
        oprot.writeFieldBegin(METADATA_FIELD_DESC);
        struct.metadata.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      if (struct.isSetEditTimestamp()) {
        oprot.writeFieldBegin(EDIT_TIMESTAMP_FIELD_DESC);
        oprot.writeI64(struct.editTimestamp);
        oprot.writeFieldEnd();
      }
      if (struct.pollInfo != null) {
        if (struct.isSetPollInfo()) {
          oprot.writeFieldBegin(POLL_INFO_FIELD_DESC);
          struct.pollInfo.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      if (struct.remoteUrl != null) {
        if (struct.isSetRemoteUrl()) {
          oprot.writeFieldBegin(REMOTE_URL_FIELD_DESC);
          oprot.writeString(struct.remoteUrl);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class StatusResultTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public StatusResultTupleScheme getScheme() {
      return new StatusResultTupleScheme();
    }
  }

  private static class StatusResultTupleScheme extends org.apache.thrift.scheme.TupleScheme<StatusResult> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, StatusResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.author.write(oprot);
      struct.content.write(oprot);
      struct.metadata.write(oprot);
      oprot.writeI64(struct.timestamp);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetEditTimestamp()) {
        optionals.set(0);
      }
      if (struct.isSetPollInfo()) {
        optionals.set(1);
      }
      if (struct.isSetRemoteUrl()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetEditTimestamp()) {
        oprot.writeI64(struct.editTimestamp);
      }
      if (struct.isSetPollInfo()) {
        struct.pollInfo.write(oprot);
      }
      if (struct.isSetRemoteUrl()) {
        oprot.writeString(struct.remoteUrl);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, StatusResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.author = new AccountWithId();
      struct.author.read(iprot);
      struct.setAuthorIsSet(true);
      struct.content = new StatusResultContent();
      struct.content.read(iprot);
      struct.setContentIsSet(true);
      struct.metadata = new StatusMetadata();
      struct.metadata.read(iprot);
      struct.setMetadataIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.editTimestamp = iprot.readI64();
        struct.setEditTimestampIsSet(true);
      }
      if (incoming.get(1)) {
        struct.pollInfo = new PollInfo();
        struct.pollInfo.read(iprot);
        struct.setPollInfoIsSet(true);
      }
      if (incoming.get(2)) {
        struct.remoteUrl = iprot.readString();
        struct.setRemoteUrlIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

