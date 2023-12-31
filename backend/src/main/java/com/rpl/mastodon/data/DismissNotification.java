/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class DismissNotification implements org.apache.thrift.TBase<DismissNotification, DismissNotification._Fields>, java.io.Serializable, Cloneable, Comparable<DismissNotification> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("DismissNotification");

  private static final org.apache.thrift.protocol.TField ACCOUNT_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("accountId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField NOTIFICATION_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("notificationId", org.apache.thrift.protocol.TType.I64, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new DismissNotificationStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new DismissNotificationTupleSchemeFactory();

  public long accountId; // required
  public long notificationId; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ACCOUNT_ID((short)1, "accountId"),
    NOTIFICATION_ID((short)2, "notificationId");

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
        case 2: // NOTIFICATION_ID
          return NOTIFICATION_ID;
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
  private static final int __NOTIFICATIONID_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.NOTIFICATION_ID};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.ACCOUNT_ID, new org.apache.thrift.meta_data.FieldMetaData("accountId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.NOTIFICATION_ID, new org.apache.thrift.meta_data.FieldMetaData("notificationId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(DismissNotification.class, metaDataMap);
  }

  public DismissNotification() {
  }

  public DismissNotification(
    long accountId)
  {
    this();
    this.accountId = accountId;
    setAccountIdIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public DismissNotification(DismissNotification other) {
    __isset_bitfield = other.__isset_bitfield;
    this.accountId = other.accountId;
    this.notificationId = other.notificationId;
  }

  @Override
  public DismissNotification deepCopy() {
    return new DismissNotification(this);
  }

  @Override
  public void clear() {
    setAccountIdIsSet(false);
    this.accountId = 0;
    setNotificationIdIsSet(false);
    this.notificationId = 0;
  }

  public long getAccountId() {
    return this.accountId;
  }

  public DismissNotification setAccountId(long accountId) {
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

  public long getNotificationId() {
    return this.notificationId;
  }

  public DismissNotification setNotificationId(long notificationId) {
    this.notificationId = notificationId;
    setNotificationIdIsSet(true);
    return this;
  }

  public void unsetNotificationId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __NOTIFICATIONID_ISSET_ID);
  }

  /** Returns true if field notificationId is set (has been assigned a value) and false otherwise */
  public boolean isSetNotificationId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __NOTIFICATIONID_ISSET_ID);
  }

  public void setNotificationIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __NOTIFICATIONID_ISSET_ID, value);
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

    case NOTIFICATION_ID:
      if (value == null) {
        unsetNotificationId();
      } else {
        setNotificationId((java.lang.Long)value);
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

    case NOTIFICATION_ID:
      return getNotificationId();

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
    case NOTIFICATION_ID:
      return isSetNotificationId();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof DismissNotification)
      return this.equals((DismissNotification)that);
    return false;
  }

  public boolean equals(DismissNotification that) {
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

    boolean this_present_notificationId = true && this.isSetNotificationId();
    boolean that_present_notificationId = true && that.isSetNotificationId();
    if (this_present_notificationId || that_present_notificationId) {
      if (!(this_present_notificationId && that_present_notificationId))
        return false;
      if (this.notificationId != that.notificationId)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(accountId);

    hashCode = hashCode * 8191 + ((isSetNotificationId()) ? 131071 : 524287);
    if (isSetNotificationId())
      hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(notificationId);

    return hashCode;
  }

  @Override
  public int compareTo(DismissNotification other) {
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
    lastComparison = java.lang.Boolean.compare(isSetNotificationId(), other.isSetNotificationId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNotificationId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.notificationId, other.notificationId);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("DismissNotification(");
    boolean first = true;

    sb.append("accountId:");
    sb.append(this.accountId);
    first = false;
    if (isSetNotificationId()) {
      if (!first) sb.append(", ");
      sb.append("notificationId:");
      sb.append(this.notificationId);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'accountId' because it's a primitive and you chose the non-beans generator.
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

  private static class DismissNotificationStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public DismissNotificationStandardScheme getScheme() {
      return new DismissNotificationStandardScheme();
    }
  }

  private static class DismissNotificationStandardScheme extends org.apache.thrift.scheme.StandardScheme<DismissNotification> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, DismissNotification struct) throws org.apache.thrift.TException {
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
          case 2: // NOTIFICATION_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.notificationId = iprot.readI64();
              struct.setNotificationIdIsSet(true);
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
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, DismissNotification struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(ACCOUNT_ID_FIELD_DESC);
      oprot.writeI64(struct.accountId);
      oprot.writeFieldEnd();
      if (struct.isSetNotificationId()) {
        oprot.writeFieldBegin(NOTIFICATION_ID_FIELD_DESC);
        oprot.writeI64(struct.notificationId);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class DismissNotificationTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public DismissNotificationTupleScheme getScheme() {
      return new DismissNotificationTupleScheme();
    }
  }

  private static class DismissNotificationTupleScheme extends org.apache.thrift.scheme.TupleScheme<DismissNotification> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, DismissNotification struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.accountId);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetNotificationId()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetNotificationId()) {
        oprot.writeI64(struct.notificationId);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, DismissNotification struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.accountId = iprot.readI64();
      struct.setAccountIdIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.notificationId = iprot.readI64();
        struct.setNotificationIdIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

