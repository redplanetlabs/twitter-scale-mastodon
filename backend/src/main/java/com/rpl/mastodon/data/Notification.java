/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class Notification implements org.apache.thrift.TBase<Notification, Notification._Fields>, java.io.Serializable, Cloneable, Comparable<Notification> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("Notification");

  private static final org.apache.thrift.protocol.TField CONTENT_FIELD_DESC = new org.apache.thrift.protocol.TField("content", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new NotificationStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new NotificationTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable NotificationContent content; // required
  public long timestamp; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    CONTENT((short)1, "content"),
    TIMESTAMP((short)2, "timestamp");

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
        case 1: // CONTENT
          return CONTENT;
        case 2: // TIMESTAMP
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
  private static final int __TIMESTAMP_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.CONTENT, new org.apache.thrift.meta_data.FieldMetaData("content", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, NotificationContent.class)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(Notification.class, metaDataMap);
  }

  public Notification() {
  }

  public Notification(
    NotificationContent content,
    long timestamp)
  {
    this();
    this.content = content;
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public Notification(Notification other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetContent()) {
      this.content = new NotificationContent(other.content);
    }
    this.timestamp = other.timestamp;
  }

  @Override
  public Notification deepCopy() {
    return new Notification(this);
  }

  @Override
  public void clear() {
    this.content = null;
    setTimestampIsSet(false);
    this.timestamp = 0;
  }

  @org.apache.thrift.annotation.Nullable
  public NotificationContent getContent() {
    return this.content;
  }

  public Notification setContent(@org.apache.thrift.annotation.Nullable NotificationContent content) {
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

  public Notification setTimestamp(long timestamp) {
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
    case CONTENT:
      if (value == null) {
        unsetContent();
      } else {
        setContent((NotificationContent)value);
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
    case CONTENT:
      return getContent();

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
    case CONTENT:
      return isSetContent();
    case TIMESTAMP:
      return isSetTimestamp();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof Notification)
      return this.equals((Notification)that);
    return false;
  }

  public boolean equals(Notification that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

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

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetContent()) ? 131071 : 524287);
    if (isSetContent())
      hashCode = hashCode * 8191 + content.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    return hashCode;
  }

  @Override
  public int compareTo(Notification other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("Notification(");
    boolean first = true;

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
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
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

  private static class NotificationStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public NotificationStandardScheme getScheme() {
      return new NotificationStandardScheme();
    }
  }

  private static class NotificationStandardScheme extends org.apache.thrift.scheme.StandardScheme<Notification> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, Notification struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // CONTENT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.content = new NotificationContent();
              struct.content.read(iprot);
              struct.setContentIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // TIMESTAMP
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
      if (!struct.isSetTimestamp()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'timestamp' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, Notification struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.content != null) {
        oprot.writeFieldBegin(CONTENT_FIELD_DESC);
        struct.content.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class NotificationTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public NotificationTupleScheme getScheme() {
      return new NotificationTupleScheme();
    }
  }

  private static class NotificationTupleScheme extends org.apache.thrift.scheme.TupleScheme<Notification> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, Notification struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.content.write(oprot);
      oprot.writeI64(struct.timestamp);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, Notification struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.content = new NotificationContent();
      struct.content.read(iprot);
      struct.setContentIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

