/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class AddScheduledStatus implements org.apache.thrift.TBase<AddScheduledStatus, AddScheduledStatus._Fields>, java.io.Serializable, Cloneable, Comparable<AddScheduledStatus> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("AddScheduledStatus");

  private static final org.apache.thrift.protocol.TField UUID_FIELD_DESC = new org.apache.thrift.protocol.TField("uuid", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField STATUS_FIELD_DESC = new org.apache.thrift.protocol.TField("status", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField PUBLISH_MILLIS_FIELD_DESC = new org.apache.thrift.protocol.TField("publishMillis", org.apache.thrift.protocol.TType.I64, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new AddScheduledStatusStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new AddScheduledStatusTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.lang.String uuid; // required
  public @org.apache.thrift.annotation.Nullable Status status; // required
  public long publishMillis; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    UUID((short)1, "uuid"),
    STATUS((short)2, "status"),
    PUBLISH_MILLIS((short)3, "publishMillis");

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
        case 1: // UUID
          return UUID;
        case 2: // STATUS
          return STATUS;
        case 3: // PUBLISH_MILLIS
          return PUBLISH_MILLIS;
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
  private static final int __PUBLISHMILLIS_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.UUID, new org.apache.thrift.meta_data.FieldMetaData("uuid", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.STATUS, new org.apache.thrift.meta_data.FieldMetaData("status", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, Status.class)));
    tmpMap.put(_Fields.PUBLISH_MILLIS, new org.apache.thrift.meta_data.FieldMetaData("publishMillis", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(AddScheduledStatus.class, metaDataMap);
  }

  public AddScheduledStatus() {
  }

  public AddScheduledStatus(
    java.lang.String uuid,
    Status status,
    long publishMillis)
  {
    this();
    this.uuid = uuid;
    this.status = status;
    this.publishMillis = publishMillis;
    setPublishMillisIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public AddScheduledStatus(AddScheduledStatus other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetUuid()) {
      this.uuid = other.uuid;
    }
    if (other.isSetStatus()) {
      this.status = new Status(other.status);
    }
    this.publishMillis = other.publishMillis;
  }

  @Override
  public AddScheduledStatus deepCopy() {
    return new AddScheduledStatus(this);
  }

  @Override
  public void clear() {
    this.uuid = null;
    this.status = null;
    setPublishMillisIsSet(false);
    this.publishMillis = 0;
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getUuid() {
    return this.uuid;
  }

  public AddScheduledStatus setUuid(@org.apache.thrift.annotation.Nullable java.lang.String uuid) {
    this.uuid = uuid;
    return this;
  }

  public void unsetUuid() {
    this.uuid = null;
  }

  /** Returns true if field uuid is set (has been assigned a value) and false otherwise */
  public boolean isSetUuid() {
    return this.uuid != null;
  }

  public void setUuidIsSet(boolean value) {
    if (!value) {
      this.uuid = null;
    }
  }

  @org.apache.thrift.annotation.Nullable
  public Status getStatus() {
    return this.status;
  }

  public AddScheduledStatus setStatus(@org.apache.thrift.annotation.Nullable Status status) {
    this.status = status;
    return this;
  }

  public void unsetStatus() {
    this.status = null;
  }

  /** Returns true if field status is set (has been assigned a value) and false otherwise */
  public boolean isSetStatus() {
    return this.status != null;
  }

  public void setStatusIsSet(boolean value) {
    if (!value) {
      this.status = null;
    }
  }

  public long getPublishMillis() {
    return this.publishMillis;
  }

  public AddScheduledStatus setPublishMillis(long publishMillis) {
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

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case UUID:
      if (value == null) {
        unsetUuid();
      } else {
        setUuid((java.lang.String)value);
      }
      break;

    case STATUS:
      if (value == null) {
        unsetStatus();
      } else {
        setStatus((Status)value);
      }
      break;

    case PUBLISH_MILLIS:
      if (value == null) {
        unsetPublishMillis();
      } else {
        setPublishMillis((java.lang.Long)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case UUID:
      return getUuid();

    case STATUS:
      return getStatus();

    case PUBLISH_MILLIS:
      return getPublishMillis();

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
    case UUID:
      return isSetUuid();
    case STATUS:
      return isSetStatus();
    case PUBLISH_MILLIS:
      return isSetPublishMillis();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof AddScheduledStatus)
      return this.equals((AddScheduledStatus)that);
    return false;
  }

  public boolean equals(AddScheduledStatus that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_uuid = true && this.isSetUuid();
    boolean that_present_uuid = true && that.isSetUuid();
    if (this_present_uuid || that_present_uuid) {
      if (!(this_present_uuid && that_present_uuid))
        return false;
      if (!this.uuid.equals(that.uuid))
        return false;
    }

    boolean this_present_status = true && this.isSetStatus();
    boolean that_present_status = true && that.isSetStatus();
    if (this_present_status || that_present_status) {
      if (!(this_present_status && that_present_status))
        return false;
      if (!this.status.equals(that.status))
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

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetUuid()) ? 131071 : 524287);
    if (isSetUuid())
      hashCode = hashCode * 8191 + uuid.hashCode();

    hashCode = hashCode * 8191 + ((isSetStatus()) ? 131071 : 524287);
    if (isSetStatus())
      hashCode = hashCode * 8191 + status.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(publishMillis);

    return hashCode;
  }

  @Override
  public int compareTo(AddScheduledStatus other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetUuid(), other.isSetUuid());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetUuid()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.uuid, other.uuid);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetStatus(), other.isSetStatus());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStatus()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.status, other.status);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("AddScheduledStatus(");
    boolean first = true;

    sb.append("uuid:");
    if (this.uuid == null) {
      sb.append("null");
    } else {
      sb.append(this.uuid);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("status:");
    if (this.status == null) {
      sb.append("null");
    } else {
      sb.append(this.status);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("publishMillis:");
    sb.append(this.publishMillis);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (uuid == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'uuid' was not present! Struct: " + toString());
    }
    if (status == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'status' was not present! Struct: " + toString());
    }
    // alas, we cannot check 'publishMillis' because it's a primitive and you chose the non-beans generator.
    // check for sub-struct validity
    if (status != null) {
      status.validate();
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

  private static class AddScheduledStatusStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public AddScheduledStatusStandardScheme getScheme() {
      return new AddScheduledStatusStandardScheme();
    }
  }

  private static class AddScheduledStatusStandardScheme extends org.apache.thrift.scheme.StandardScheme<AddScheduledStatus> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, AddScheduledStatus struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // UUID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.uuid = iprot.readString();
              struct.setUuidIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // STATUS
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.status = new Status();
              struct.status.read(iprot);
              struct.setStatusIsSet(true);
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
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      if (!struct.isSetPublishMillis()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'publishMillis' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, AddScheduledStatus struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.uuid != null) {
        oprot.writeFieldBegin(UUID_FIELD_DESC);
        oprot.writeString(struct.uuid);
        oprot.writeFieldEnd();
      }
      if (struct.status != null) {
        oprot.writeFieldBegin(STATUS_FIELD_DESC);
        struct.status.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(PUBLISH_MILLIS_FIELD_DESC);
      oprot.writeI64(struct.publishMillis);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class AddScheduledStatusTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public AddScheduledStatusTupleScheme getScheme() {
      return new AddScheduledStatusTupleScheme();
    }
  }

  private static class AddScheduledStatusTupleScheme extends org.apache.thrift.scheme.TupleScheme<AddScheduledStatus> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, AddScheduledStatus struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeString(struct.uuid);
      struct.status.write(oprot);
      oprot.writeI64(struct.publishMillis);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, AddScheduledStatus struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.uuid = iprot.readString();
      struct.setUuidIsSet(true);
      struct.status = new Status();
      struct.status.read(iprot);
      struct.setStatusIsSet(true);
      struct.publishMillis = iprot.readI64();
      struct.setPublishMillisIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

