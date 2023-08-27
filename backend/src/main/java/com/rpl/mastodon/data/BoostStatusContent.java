/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class BoostStatusContent implements org.apache.thrift.TBase<BoostStatusContent, BoostStatusContent._Fields>, java.io.Serializable, Cloneable, Comparable<BoostStatusContent> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("BoostStatusContent");

  private static final org.apache.thrift.protocol.TField BOOSTED_FIELD_DESC = new org.apache.thrift.protocol.TField("boosted", org.apache.thrift.protocol.TType.STRUCT, (short)1);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new BoostStatusContentStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new BoostStatusContentTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable StatusPointer boosted; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    BOOSTED((short)1, "boosted");

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
        case 1: // BOOSTED
          return BOOSTED;
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
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.BOOSTED, new org.apache.thrift.meta_data.FieldMetaData("boosted", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StatusPointer.class)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(BoostStatusContent.class, metaDataMap);
  }

  public BoostStatusContent() {
  }

  public BoostStatusContent(
    StatusPointer boosted)
  {
    this();
    this.boosted = boosted;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public BoostStatusContent(BoostStatusContent other) {
    if (other.isSetBoosted()) {
      this.boosted = new StatusPointer(other.boosted);
    }
  }

  @Override
  public BoostStatusContent deepCopy() {
    return new BoostStatusContent(this);
  }

  @Override
  public void clear() {
    this.boosted = null;
  }

  @org.apache.thrift.annotation.Nullable
  public StatusPointer getBoosted() {
    return this.boosted;
  }

  public BoostStatusContent setBoosted(@org.apache.thrift.annotation.Nullable StatusPointer boosted) {
    this.boosted = boosted;
    return this;
  }

  public void unsetBoosted() {
    this.boosted = null;
  }

  /** Returns true if field boosted is set (has been assigned a value) and false otherwise */
  public boolean isSetBoosted() {
    return this.boosted != null;
  }

  public void setBoostedIsSet(boolean value) {
    if (!value) {
      this.boosted = null;
    }
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case BOOSTED:
      if (value == null) {
        unsetBoosted();
      } else {
        setBoosted((StatusPointer)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case BOOSTED:
      return getBoosted();

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
    case BOOSTED:
      return isSetBoosted();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof BoostStatusContent)
      return this.equals((BoostStatusContent)that);
    return false;
  }

  public boolean equals(BoostStatusContent that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_boosted = true && this.isSetBoosted();
    boolean that_present_boosted = true && that.isSetBoosted();
    if (this_present_boosted || that_present_boosted) {
      if (!(this_present_boosted && that_present_boosted))
        return false;
      if (!this.boosted.equals(that.boosted))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetBoosted()) ? 131071 : 524287);
    if (isSetBoosted())
      hashCode = hashCode * 8191 + boosted.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(BoostStatusContent other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetBoosted(), other.isSetBoosted());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBoosted()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.boosted, other.boosted);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("BoostStatusContent(");
    boolean first = true;

    sb.append("boosted:");
    if (this.boosted == null) {
      sb.append("null");
    } else {
      sb.append(this.boosted);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (boosted == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'boosted' was not present! Struct: " + toString());
    }
    // check for sub-struct validity
    if (boosted != null) {
      boosted.validate();
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
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class BoostStatusContentStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public BoostStatusContentStandardScheme getScheme() {
      return new BoostStatusContentStandardScheme();
    }
  }

  private static class BoostStatusContentStandardScheme extends org.apache.thrift.scheme.StandardScheme<BoostStatusContent> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, BoostStatusContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // BOOSTED
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.boosted = new StatusPointer();
              struct.boosted.read(iprot);
              struct.setBoostedIsSet(true);
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
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, BoostStatusContent struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.boosted != null) {
        oprot.writeFieldBegin(BOOSTED_FIELD_DESC);
        struct.boosted.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class BoostStatusContentTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public BoostStatusContentTupleScheme getScheme() {
      return new BoostStatusContentTupleScheme();
    }
  }

  private static class BoostStatusContentTupleScheme extends org.apache.thrift.scheme.TupleScheme<BoostStatusContent> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, BoostStatusContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.boosted.write(oprot);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, BoostStatusContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.boosted = new StatusPointer();
      struct.boosted.read(iprot);
      struct.setBoostedIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

