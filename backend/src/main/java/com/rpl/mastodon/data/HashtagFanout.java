/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class HashtagFanout implements org.apache.thrift.TBase<HashtagFanout, HashtagFanout._Fields>, java.io.Serializable, Cloneable, Comparable<HashtagFanout> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("HashtagFanout");

  private static final org.apache.thrift.protocol.TField AUTHOR_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("authorId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField STATUS_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("statusId", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField HASHTAG_FIELD_DESC = new org.apache.thrift.protocol.TField("hashtag", org.apache.thrift.protocol.TType.STRING, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new HashtagFanoutStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new HashtagFanoutTupleSchemeFactory();

  public long authorId; // required
  public long statusId; // required
  public @org.apache.thrift.annotation.Nullable java.lang.String hashtag; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    AUTHOR_ID((short)1, "authorId"),
    STATUS_ID((short)2, "statusId"),
    HASHTAG((short)3, "hashtag");

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
        case 2: // STATUS_ID
          return STATUS_ID;
        case 3: // HASHTAG
          return HASHTAG;
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
  private static final int __STATUSID_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.AUTHOR_ID, new org.apache.thrift.meta_data.FieldMetaData("authorId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.STATUS_ID, new org.apache.thrift.meta_data.FieldMetaData("statusId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "StatusId")));
    tmpMap.put(_Fields.HASHTAG, new org.apache.thrift.meta_data.FieldMetaData("hashtag", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(HashtagFanout.class, metaDataMap);
  }

  public HashtagFanout() {
  }

  public HashtagFanout(
    long authorId,
    long statusId,
    java.lang.String hashtag)
  {
    this();
    this.authorId = authorId;
    setAuthorIdIsSet(true);
    this.statusId = statusId;
    setStatusIdIsSet(true);
    this.hashtag = hashtag;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public HashtagFanout(HashtagFanout other) {
    __isset_bitfield = other.__isset_bitfield;
    this.authorId = other.authorId;
    this.statusId = other.statusId;
    if (other.isSetHashtag()) {
      this.hashtag = other.hashtag;
    }
  }

  @Override
  public HashtagFanout deepCopy() {
    return new HashtagFanout(this);
  }

  @Override
  public void clear() {
    setAuthorIdIsSet(false);
    this.authorId = 0;
    setStatusIdIsSet(false);
    this.statusId = 0;
    this.hashtag = null;
  }

  public long getAuthorId() {
    return this.authorId;
  }

  public HashtagFanout setAuthorId(long authorId) {
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

  public long getStatusId() {
    return this.statusId;
  }

  public HashtagFanout setStatusId(long statusId) {
    this.statusId = statusId;
    setStatusIdIsSet(true);
    return this;
  }

  public void unsetStatusId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __STATUSID_ISSET_ID);
  }

  /** Returns true if field statusId is set (has been assigned a value) and false otherwise */
  public boolean isSetStatusId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __STATUSID_ISSET_ID);
  }

  public void setStatusIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __STATUSID_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getHashtag() {
    return this.hashtag;
  }

  public HashtagFanout setHashtag(@org.apache.thrift.annotation.Nullable java.lang.String hashtag) {
    this.hashtag = hashtag;
    return this;
  }

  public void unsetHashtag() {
    this.hashtag = null;
  }

  /** Returns true if field hashtag is set (has been assigned a value) and false otherwise */
  public boolean isSetHashtag() {
    return this.hashtag != null;
  }

  public void setHashtagIsSet(boolean value) {
    if (!value) {
      this.hashtag = null;
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

    case STATUS_ID:
      if (value == null) {
        unsetStatusId();
      } else {
        setStatusId((java.lang.Long)value);
      }
      break;

    case HASHTAG:
      if (value == null) {
        unsetHashtag();
      } else {
        setHashtag((java.lang.String)value);
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

    case STATUS_ID:
      return getStatusId();

    case HASHTAG:
      return getHashtag();

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
    case STATUS_ID:
      return isSetStatusId();
    case HASHTAG:
      return isSetHashtag();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof HashtagFanout)
      return this.equals((HashtagFanout)that);
    return false;
  }

  public boolean equals(HashtagFanout that) {
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

    boolean this_present_statusId = true;
    boolean that_present_statusId = true;
    if (this_present_statusId || that_present_statusId) {
      if (!(this_present_statusId && that_present_statusId))
        return false;
      if (this.statusId != that.statusId)
        return false;
    }

    boolean this_present_hashtag = true && this.isSetHashtag();
    boolean that_present_hashtag = true && that.isSetHashtag();
    if (this_present_hashtag || that_present_hashtag) {
      if (!(this_present_hashtag && that_present_hashtag))
        return false;
      if (!this.hashtag.equals(that.hashtag))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(authorId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(statusId);

    hashCode = hashCode * 8191 + ((isSetHashtag()) ? 131071 : 524287);
    if (isSetHashtag())
      hashCode = hashCode * 8191 + hashtag.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(HashtagFanout other) {
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
    lastComparison = java.lang.Boolean.compare(isSetStatusId(), other.isSetStatusId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStatusId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.statusId, other.statusId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetHashtag(), other.isSetHashtag());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetHashtag()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.hashtag, other.hashtag);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("HashtagFanout(");
    boolean first = true;

    sb.append("authorId:");
    sb.append(this.authorId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("statusId:");
    sb.append(this.statusId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("hashtag:");
    if (this.hashtag == null) {
      sb.append("null");
    } else {
      sb.append(this.hashtag);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'authorId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'statusId' because it's a primitive and you chose the non-beans generator.
    if (hashtag == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'hashtag' was not present! Struct: " + toString());
    }
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

  private static class HashtagFanoutStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public HashtagFanoutStandardScheme getScheme() {
      return new HashtagFanoutStandardScheme();
    }
  }

  private static class HashtagFanoutStandardScheme extends org.apache.thrift.scheme.StandardScheme<HashtagFanout> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, HashtagFanout struct) throws org.apache.thrift.TException {
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
          case 2: // STATUS_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.statusId = iprot.readI64();
              struct.setStatusIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // HASHTAG
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.hashtag = iprot.readString();
              struct.setHashtagIsSet(true);
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
      if (!struct.isSetStatusId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'statusId' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, HashtagFanout struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(AUTHOR_ID_FIELD_DESC);
      oprot.writeI64(struct.authorId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(STATUS_ID_FIELD_DESC);
      oprot.writeI64(struct.statusId);
      oprot.writeFieldEnd();
      if (struct.hashtag != null) {
        oprot.writeFieldBegin(HASHTAG_FIELD_DESC);
        oprot.writeString(struct.hashtag);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class HashtagFanoutTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public HashtagFanoutTupleScheme getScheme() {
      return new HashtagFanoutTupleScheme();
    }
  }

  private static class HashtagFanoutTupleScheme extends org.apache.thrift.scheme.TupleScheme<HashtagFanout> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, HashtagFanout struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.authorId);
      oprot.writeI64(struct.statusId);
      oprot.writeString(struct.hashtag);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, HashtagFanout struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.authorId = iprot.readI64();
      struct.setAuthorIdIsSet(true);
      struct.statusId = iprot.readI64();
      struct.setStatusIdIsSet(true);
      struct.hashtag = iprot.readString();
      struct.setHashtagIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

