/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class MuteAccountOptions implements org.apache.thrift.TBase<MuteAccountOptions, MuteAccountOptions._Fields>, java.io.Serializable, Cloneable, Comparable<MuteAccountOptions> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("MuteAccountOptions");

  private static final org.apache.thrift.protocol.TField MUTE_NOTIFICATIONS_FIELD_DESC = new org.apache.thrift.protocol.TField("muteNotifications", org.apache.thrift.protocol.TType.BOOL, (short)1);
  private static final org.apache.thrift.protocol.TField EXPIRATION_MILLIS_FIELD_DESC = new org.apache.thrift.protocol.TField("expirationMillis", org.apache.thrift.protocol.TType.I64, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new MuteAccountOptionsStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new MuteAccountOptionsTupleSchemeFactory();

  public boolean muteNotifications; // required
  public long expirationMillis; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    MUTE_NOTIFICATIONS((short)1, "muteNotifications"),
    EXPIRATION_MILLIS((short)2, "expirationMillis");

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
        case 1: // MUTE_NOTIFICATIONS
          return MUTE_NOTIFICATIONS;
        case 2: // EXPIRATION_MILLIS
          return EXPIRATION_MILLIS;
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
  private static final int __MUTENOTIFICATIONS_ISSET_ID = 0;
  private static final int __EXPIRATIONMILLIS_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.EXPIRATION_MILLIS};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.MUTE_NOTIFICATIONS, new org.apache.thrift.meta_data.FieldMetaData("muteNotifications", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    tmpMap.put(_Fields.EXPIRATION_MILLIS, new org.apache.thrift.meta_data.FieldMetaData("expirationMillis", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(MuteAccountOptions.class, metaDataMap);
  }

  public MuteAccountOptions() {
  }

  public MuteAccountOptions(
    boolean muteNotifications)
  {
    this();
    this.muteNotifications = muteNotifications;
    setMuteNotificationsIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public MuteAccountOptions(MuteAccountOptions other) {
    __isset_bitfield = other.__isset_bitfield;
    this.muteNotifications = other.muteNotifications;
    this.expirationMillis = other.expirationMillis;
  }

  @Override
  public MuteAccountOptions deepCopy() {
    return new MuteAccountOptions(this);
  }

  @Override
  public void clear() {
    setMuteNotificationsIsSet(false);
    this.muteNotifications = false;
    setExpirationMillisIsSet(false);
    this.expirationMillis = 0;
  }

  public boolean isMuteNotifications() {
    return this.muteNotifications;
  }

  public MuteAccountOptions setMuteNotifications(boolean muteNotifications) {
    this.muteNotifications = muteNotifications;
    setMuteNotificationsIsSet(true);
    return this;
  }

  public void unsetMuteNotifications() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __MUTENOTIFICATIONS_ISSET_ID);
  }

  /** Returns true if field muteNotifications is set (has been assigned a value) and false otherwise */
  public boolean isSetMuteNotifications() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __MUTENOTIFICATIONS_ISSET_ID);
  }

  public void setMuteNotificationsIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __MUTENOTIFICATIONS_ISSET_ID, value);
  }

  public long getExpirationMillis() {
    return this.expirationMillis;
  }

  public MuteAccountOptions setExpirationMillis(long expirationMillis) {
    this.expirationMillis = expirationMillis;
    setExpirationMillisIsSet(true);
    return this;
  }

  public void unsetExpirationMillis() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __EXPIRATIONMILLIS_ISSET_ID);
  }

  /** Returns true if field expirationMillis is set (has been assigned a value) and false otherwise */
  public boolean isSetExpirationMillis() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __EXPIRATIONMILLIS_ISSET_ID);
  }

  public void setExpirationMillisIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __EXPIRATIONMILLIS_ISSET_ID, value);
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case MUTE_NOTIFICATIONS:
      if (value == null) {
        unsetMuteNotifications();
      } else {
        setMuteNotifications((java.lang.Boolean)value);
      }
      break;

    case EXPIRATION_MILLIS:
      if (value == null) {
        unsetExpirationMillis();
      } else {
        setExpirationMillis((java.lang.Long)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case MUTE_NOTIFICATIONS:
      return isMuteNotifications();

    case EXPIRATION_MILLIS:
      return getExpirationMillis();

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
    case MUTE_NOTIFICATIONS:
      return isSetMuteNotifications();
    case EXPIRATION_MILLIS:
      return isSetExpirationMillis();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof MuteAccountOptions)
      return this.equals((MuteAccountOptions)that);
    return false;
  }

  public boolean equals(MuteAccountOptions that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_muteNotifications = true;
    boolean that_present_muteNotifications = true;
    if (this_present_muteNotifications || that_present_muteNotifications) {
      if (!(this_present_muteNotifications && that_present_muteNotifications))
        return false;
      if (this.muteNotifications != that.muteNotifications)
        return false;
    }

    boolean this_present_expirationMillis = true && this.isSetExpirationMillis();
    boolean that_present_expirationMillis = true && that.isSetExpirationMillis();
    if (this_present_expirationMillis || that_present_expirationMillis) {
      if (!(this_present_expirationMillis && that_present_expirationMillis))
        return false;
      if (this.expirationMillis != that.expirationMillis)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((muteNotifications) ? 131071 : 524287);

    hashCode = hashCode * 8191 + ((isSetExpirationMillis()) ? 131071 : 524287);
    if (isSetExpirationMillis())
      hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(expirationMillis);

    return hashCode;
  }

  @Override
  public int compareTo(MuteAccountOptions other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetMuteNotifications(), other.isSetMuteNotifications());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMuteNotifications()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.muteNotifications, other.muteNotifications);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetExpirationMillis(), other.isSetExpirationMillis());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetExpirationMillis()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.expirationMillis, other.expirationMillis);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("MuteAccountOptions(");
    boolean first = true;

    sb.append("muteNotifications:");
    sb.append(this.muteNotifications);
    first = false;
    if (isSetExpirationMillis()) {
      if (!first) sb.append(", ");
      sb.append("expirationMillis:");
      sb.append(this.expirationMillis);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'muteNotifications' because it's a primitive and you chose the non-beans generator.
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

  private static class MuteAccountOptionsStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public MuteAccountOptionsStandardScheme getScheme() {
      return new MuteAccountOptionsStandardScheme();
    }
  }

  private static class MuteAccountOptionsStandardScheme extends org.apache.thrift.scheme.StandardScheme<MuteAccountOptions> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, MuteAccountOptions struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // MUTE_NOTIFICATIONS
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.muteNotifications = iprot.readBool();
              struct.setMuteNotificationsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // EXPIRATION_MILLIS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.expirationMillis = iprot.readI64();
              struct.setExpirationMillisIsSet(true);
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
      if (!struct.isSetMuteNotifications()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'muteNotifications' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, MuteAccountOptions struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(MUTE_NOTIFICATIONS_FIELD_DESC);
      oprot.writeBool(struct.muteNotifications);
      oprot.writeFieldEnd();
      if (struct.isSetExpirationMillis()) {
        oprot.writeFieldBegin(EXPIRATION_MILLIS_FIELD_DESC);
        oprot.writeI64(struct.expirationMillis);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class MuteAccountOptionsTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public MuteAccountOptionsTupleScheme getScheme() {
      return new MuteAccountOptionsTupleScheme();
    }
  }

  private static class MuteAccountOptionsTupleScheme extends org.apache.thrift.scheme.TupleScheme<MuteAccountOptions> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, MuteAccountOptions struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeBool(struct.muteNotifications);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetExpirationMillis()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetExpirationMillis()) {
        oprot.writeI64(struct.expirationMillis);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, MuteAccountOptions struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.muteNotifications = iprot.readBool();
      struct.setMuteNotificationsIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.expirationMillis = iprot.readI64();
        struct.setExpirationMillisIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

