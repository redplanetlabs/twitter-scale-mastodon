/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class EditScheduledStatusPublishTime implements org.apache.thrift.TBase<EditScheduledStatusPublishTime, EditScheduledStatusPublishTime._Fields>, java.io.Serializable, Cloneable, Comparable<EditScheduledStatusPublishTime> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("EditScheduledStatusPublishTime");

  private static final org.apache.thrift.protocol.TField ACCOUNT_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("accountId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField ID_FIELD_DESC = new org.apache.thrift.protocol.TField("id", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField PUBLISH_MILLIS_FIELD_DESC = new org.apache.thrift.protocol.TField("publishMillis", org.apache.thrift.protocol.TType.I64, (short)3);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)4);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new EditScheduledStatusPublishTimeStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new EditScheduledStatusPublishTimeTupleSchemeFactory();

  public long accountId; // required
  public long id; // required
  public long publishMillis; // required
  public long timestamp; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ACCOUNT_ID((short)1, "accountId"),
    ID((short)2, "id"),
    PUBLISH_MILLIS((short)3, "publishMillis"),
    TIMESTAMP((short)4, "timestamp");

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
        case 1: // ACCOUNT_ID
          return ACCOUNT_ID;
        case 2: // ID
          return ID;
        case 3: // PUBLISH_MILLIS
          return PUBLISH_MILLIS;
        case 4: // TIMESTAMP
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
  private static final int __ACCOUNTID_ISSET_ID = 0;
  private static final int __ID_ISSET_ID = 1;
  private static final int __PUBLISHMILLIS_ISSET_ID = 2;
  private static final int __TIMESTAMP_ISSET_ID = 3;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.ACCOUNT_ID, new org.apache.thrift.meta_data.FieldMetaData("accountId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.ID, new org.apache.thrift.meta_data.FieldMetaData("id", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.PUBLISH_MILLIS, new org.apache.thrift.meta_data.FieldMetaData("publishMillis", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(EditScheduledStatusPublishTime.class, metaDataMap);
  }

  public EditScheduledStatusPublishTime() {
  }

  public EditScheduledStatusPublishTime(
    long accountId,
    long id,
    long publishMillis,
    long timestamp)
  {
    this();
    this.accountId = accountId;
    setAccountIdIsSet(true);
    this.id = id;
    setIdIsSet(true);
    this.publishMillis = publishMillis;
    setPublishMillisIsSet(true);
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public EditScheduledStatusPublishTime(EditScheduledStatusPublishTime other) {
    __isset_bitfield = other.__isset_bitfield;
    this.accountId = other.accountId;
    this.id = other.id;
    this.publishMillis = other.publishMillis;
    this.timestamp = other.timestamp;
  }

  @Override
  public EditScheduledStatusPublishTime deepCopy() {
    return new EditScheduledStatusPublishTime(this);
  }

  @Override
  public void clear() {
    setAccountIdIsSet(false);
    this.accountId = 0;
    setIdIsSet(false);
    this.id = 0;
    setPublishMillisIsSet(false);
    this.publishMillis = 0;
    setTimestampIsSet(false);
    this.timestamp = 0;
  }

  public long getAccountId() {
    return this.accountId;
  }

  public EditScheduledStatusPublishTime setAccountId(long accountId) {
    this.accountId = accountId;
    setAccountIdIsSet(true);
    return this;
  }

  public void unsetAccountId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __ACCOUNTID_ISSET_ID);
  }

  /** Returns true if field accountId is set (has been assigned a value) and false otherwise */
  public boolean isSetAccountId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __ACCOUNTID_ISSET_ID);
  }

  public void setAccountIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __ACCOUNTID_ISSET_ID, value);
  }

  public long getId() {
    return this.id;
  }

  public EditScheduledStatusPublishTime setId(long id) {
    this.id = id;
    setIdIsSet(true);
    return this;
  }

  public void unsetId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __ID_ISSET_ID);
  }

  /** Returns true if field id is set (has been assigned a value) and false otherwise */
  public boolean isSetId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __ID_ISSET_ID);
  }

  public void setIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __ID_ISSET_ID, value);
  }

  public long getPublishMillis() {
    return this.publishMillis;
  }

  public EditScheduledStatusPublishTime setPublishMillis(long publishMillis) {
    this.publishMillis = publishMillis;
    setPublishMillisIsSet(true);
    return this;
  }

  public void unsetPublishMillis() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __PUBLISHMILLIS_ISSET_ID);
  }

  /** Returns true if field publishMillis is set (has been assigned a value) and false otherwise */
  public boolean isSetPublishMillis() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __PUBLISHMILLIS_ISSET_ID);
  }

  public void setPublishMillisIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __PUBLISHMILLIS_ISSET_ID, value);
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public EditScheduledStatusPublishTime setTimestamp(long timestamp) {
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
    case ACCOUNT_ID:
      if (value == null) {
        unsetAccountId();
      } else {
        setAccountId((java.lang.Long)value);
      }
      break;

    case ID:
      if (value == null) {
        unsetId();
      } else {
        setId((java.lang.Long)value);
      }
      break;

    case PUBLISH_MILLIS:
      if (value == null) {
        unsetPublishMillis();
      } else {
        setPublishMillis((java.lang.Long)value);
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
    case ACCOUNT_ID:
      return getAccountId();

    case ID:
      return getId();

    case PUBLISH_MILLIS:
      return getPublishMillis();

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
    case ACCOUNT_ID:
      return isSetAccountId();
    case ID:
      return isSetId();
    case PUBLISH_MILLIS:
      return isSetPublishMillis();
    case TIMESTAMP:
      return isSetTimestamp();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof EditScheduledStatusPublishTime)
      return this.equals((EditScheduledStatusPublishTime)that);
    return false;
  }

  public boolean equals(EditScheduledStatusPublishTime that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_accountId = true;
    boolean that_present_accountId = true;
    if (this_present_accountId || that_present_accountId) {
      if (!(this_present_accountId && that_present_accountId))
        return false;
      if (this.accountId != that.accountId)
        return false;
    }

    boolean this_present_id = true;
    boolean that_present_id = true;
    if (this_present_id || that_present_id) {
      if (!(this_present_id && that_present_id))
        return false;
      if (this.id != that.id)
        return false;
    }

    boolean this_present_publishMillis = true;
    boolean that_present_publishMillis = true;
    if (this_present_publishMillis || that_present_publishMillis) {
      if (!(this_present_publishMillis && that_present_publishMillis))
        return false;
      if (this.publishMillis != that.publishMillis)
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

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(accountId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(id);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(publishMillis);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    return hashCode;
  }

  @Override
  public int compareTo(EditScheduledStatusPublishTime other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetAccountId(), other.isSetAccountId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetAccountId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.accountId, other.accountId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetId(), other.isSetId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.id, other.id);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetPublishMillis(), other.isSetPublishMillis());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetPublishMillis()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.publishMillis, other.publishMillis);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("EditScheduledStatusPublishTime(");
    boolean first = true;

    sb.append("accountId:");
    sb.append(this.accountId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("id:");
    sb.append(this.id);
    first = false;
    if (!first) sb.append(", ");
    sb.append("publishMillis:");
    sb.append(this.publishMillis);
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
    // alas, we cannot check 'accountId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'id' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'publishMillis' because it's a primitive and you chose the non-beans generator.
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

  private static class EditScheduledStatusPublishTimeStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public EditScheduledStatusPublishTimeStandardScheme getScheme() {
      return new EditScheduledStatusPublishTimeStandardScheme();
    }
  }

  private static class EditScheduledStatusPublishTimeStandardScheme extends org.apache.thrift.scheme.StandardScheme<EditScheduledStatusPublishTime> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, EditScheduledStatusPublishTime struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // ACCOUNT_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.accountId = iprot.readI64();
              struct.setAccountIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.id = iprot.readI64();
              struct.setIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // PUBLISH_MILLIS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.publishMillis = iprot.readI64();
              struct.setPublishMillisIsSet(true);
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
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      if (!struct.isSetAccountId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'accountId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'id' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetPublishMillis()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'publishMillis' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetTimestamp()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'timestamp' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, EditScheduledStatusPublishTime struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(ACCOUNT_ID_FIELD_DESC);
      oprot.writeI64(struct.accountId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(ID_FIELD_DESC);
      oprot.writeI64(struct.id);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(PUBLISH_MILLIS_FIELD_DESC);
      oprot.writeI64(struct.publishMillis);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class EditScheduledStatusPublishTimeTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public EditScheduledStatusPublishTimeTupleScheme getScheme() {
      return new EditScheduledStatusPublishTimeTupleScheme();
    }
  }

  private static class EditScheduledStatusPublishTimeTupleScheme extends org.apache.thrift.scheme.TupleScheme<EditScheduledStatusPublishTime> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, EditScheduledStatusPublishTime struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.accountId);
      oprot.writeI64(struct.id);
      oprot.writeI64(struct.publishMillis);
      oprot.writeI64(struct.timestamp);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, EditScheduledStatusPublishTime struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.accountId = iprot.readI64();
      struct.setAccountIdIsSet(true);
      struct.id = iprot.readI64();
      struct.setIdIsSet(true);
      struct.publishMillis = iprot.readI64();
      struct.setPublishMillisIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

