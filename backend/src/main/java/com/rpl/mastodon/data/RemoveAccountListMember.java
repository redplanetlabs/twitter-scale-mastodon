/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class RemoveAccountListMember implements org.apache.thrift.TBase<RemoveAccountListMember, RemoveAccountListMember._Fields>, java.io.Serializable, Cloneable, Comparable<RemoveAccountListMember> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RemoveAccountListMember");

  private static final org.apache.thrift.protocol.TField LIST_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("listId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField MEMBER_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("memberId", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new RemoveAccountListMemberStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new RemoveAccountListMemberTupleSchemeFactory();

  public long listId; // required
  public long memberId; // required
  public long timestamp; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    LIST_ID((short)1, "listId"),
    MEMBER_ID((short)2, "memberId"),
    TIMESTAMP((short)3, "timestamp");

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
        case 1: // LIST_ID
          return LIST_ID;
        case 2: // MEMBER_ID
          return MEMBER_ID;
        case 3: // TIMESTAMP
          return TIMESTAMP;
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
  private static final int __LISTID_ISSET_ID = 0;
  private static final int __MEMBERID_ISSET_ID = 1;
  private static final int __TIMESTAMP_ISSET_ID = 2;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.LIST_ID, new org.apache.thrift.meta_data.FieldMetaData("listId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "ListId")));
    tmpMap.put(_Fields.MEMBER_ID, new org.apache.thrift.meta_data.FieldMetaData("memberId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RemoveAccountListMember.class, metaDataMap);
  }

  public RemoveAccountListMember() {
  }

  public RemoveAccountListMember(
    long listId,
    long memberId,
    long timestamp)
  {
    this();
    this.listId = listId;
    setListIdIsSet(true);
    this.memberId = memberId;
    setMemberIdIsSet(true);
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RemoveAccountListMember(RemoveAccountListMember other) {
    __isset_bitfield = other.__isset_bitfield;
    this.listId = other.listId;
    this.memberId = other.memberId;
    this.timestamp = other.timestamp;
  }

  @Override
  public RemoveAccountListMember deepCopy() {
    return new RemoveAccountListMember(this);
  }

  @Override
  public void clear() {
    setListIdIsSet(false);
    this.listId = 0;
    setMemberIdIsSet(false);
    this.memberId = 0;
    setTimestampIsSet(false);
    this.timestamp = 0;
  }

  public long getListId() {
    return this.listId;
  }

  public RemoveAccountListMember setListId(long listId) {
    this.listId = listId;
    setListIdIsSet(true);
    return this;
  }

  public void unsetListId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __LISTID_ISSET_ID);
  }

  /** Returns true if field listId is set (has been assigned a value) and false otherwise */
  public boolean isSetListId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __LISTID_ISSET_ID);
  }

  public void setListIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __LISTID_ISSET_ID, value);
  }

  public long getMemberId() {
    return this.memberId;
  }

  public RemoveAccountListMember setMemberId(long memberId) {
    this.memberId = memberId;
    setMemberIdIsSet(true);
    return this;
  }

  public void unsetMemberId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __MEMBERID_ISSET_ID);
  }

  /** Returns true if field memberId is set (has been assigned a value) and false otherwise */
  public boolean isSetMemberId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __MEMBERID_ISSET_ID);
  }

  public void setMemberIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __MEMBERID_ISSET_ID, value);
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public RemoveAccountListMember setTimestamp(long timestamp) {
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

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case LIST_ID:
      if (value == null) {
        unsetListId();
      } else {
        setListId((java.lang.Long)value);
      }
      break;

    case MEMBER_ID:
      if (value == null) {
        unsetMemberId();
      } else {
        setMemberId((java.lang.Long)value);
      }
      break;

    case TIMESTAMP:
      if (value == null) {
        unsetTimestamp();
      } else {
        setTimestamp((java.lang.Long)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case LIST_ID:
      return getListId();

    case MEMBER_ID:
      return getMemberId();

    case TIMESTAMP:
      return getTimestamp();

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
    case LIST_ID:
      return isSetListId();
    case MEMBER_ID:
      return isSetMemberId();
    case TIMESTAMP:
      return isSetTimestamp();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof RemoveAccountListMember)
      return this.equals((RemoveAccountListMember)that);
    return false;
  }

  public boolean equals(RemoveAccountListMember that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_listId = true;
    boolean that_present_listId = true;
    if (this_present_listId || that_present_listId) {
      if (!(this_present_listId && that_present_listId))
        return false;
      if (this.listId != that.listId)
        return false;
    }

    boolean this_present_memberId = true;
    boolean that_present_memberId = true;
    if (this_present_memberId || that_present_memberId) {
      if (!(this_present_memberId && that_present_memberId))
        return false;
      if (this.memberId != that.memberId)
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

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(listId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(memberId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    return hashCode;
  }

  @Override
  public int compareTo(RemoveAccountListMember other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetListId(), other.isSetListId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetListId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.listId, other.listId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetMemberId(), other.isSetMemberId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMemberId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.memberId, other.memberId);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("RemoveAccountListMember(");
    boolean first = true;

    sb.append("listId:");
    sb.append(this.listId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("memberId:");
    sb.append(this.memberId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("timestamp:");
    sb.append(this.timestamp);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'listId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'memberId' because it's a primitive and you chose the non-beans generator.
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

  private static class RemoveAccountListMemberStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveAccountListMemberStandardScheme getScheme() {
      return new RemoveAccountListMemberStandardScheme();
    }
  }

  private static class RemoveAccountListMemberStandardScheme extends org.apache.thrift.scheme.StandardScheme<RemoveAccountListMember> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, RemoveAccountListMember struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // LIST_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.listId = iprot.readI64();
              struct.setListIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // MEMBER_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.memberId = iprot.readI64();
              struct.setMemberIdIsSet(true);
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
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      if (!struct.isSetListId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'listId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetMemberId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'memberId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetTimestamp()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'timestamp' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, RemoveAccountListMember struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(LIST_ID_FIELD_DESC);
      oprot.writeI64(struct.listId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(MEMBER_ID_FIELD_DESC);
      oprot.writeI64(struct.memberId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class RemoveAccountListMemberTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveAccountListMemberTupleScheme getScheme() {
      return new RemoveAccountListMemberTupleScheme();
    }
  }

  private static class RemoveAccountListMemberTupleScheme extends org.apache.thrift.scheme.TupleScheme<RemoveAccountListMember> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RemoveAccountListMember struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.listId);
      oprot.writeI64(struct.memberId);
      oprot.writeI64(struct.timestamp);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RemoveAccountListMember struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.listId = iprot.readI64();
      struct.setListIdIsSet(true);
      struct.memberId = iprot.readI64();
      struct.setMemberIdIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

