/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class RemoveStatusFromFilter implements org.apache.thrift.TBase<RemoveStatusFromFilter, RemoveStatusFromFilter._Fields>, java.io.Serializable, Cloneable, Comparable<RemoveStatusFromFilter> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RemoveStatusFromFilter");

  private static final org.apache.thrift.protocol.TField FILTER_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("filterId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField ACCOUNT_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("accountId", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField TARGET_FIELD_DESC = new org.apache.thrift.protocol.TField("target", org.apache.thrift.protocol.TType.STRUCT, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new RemoveStatusFromFilterStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new RemoveStatusFromFilterTupleSchemeFactory();

  public long filterId; // required
  public long accountId; // required
  public @org.apache.thrift.annotation.Nullable StatusPointer target; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    FILTER_ID((short)1, "filterId"),
    ACCOUNT_ID((short)2, "accountId"),
    TARGET((short)3, "target");

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
        case 1: // FILTER_ID
          return FILTER_ID;
        case 2: // ACCOUNT_ID
          return ACCOUNT_ID;
        case 3: // TARGET
          return TARGET;
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
  private static final int __FILTERID_ISSET_ID = 0;
  private static final int __ACCOUNTID_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.FILTER_ID, new org.apache.thrift.meta_data.FieldMetaData("filterId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "FilterId")));
    tmpMap.put(_Fields.ACCOUNT_ID, new org.apache.thrift.meta_data.FieldMetaData("accountId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.TARGET, new org.apache.thrift.meta_data.FieldMetaData("target", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StatusPointer.class)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RemoveStatusFromFilter.class, metaDataMap);
  }

  public RemoveStatusFromFilter() {
  }

  public RemoveStatusFromFilter(
    long filterId,
    long accountId,
    StatusPointer target)
  {
    this();
    this.filterId = filterId;
    setFilterIdIsSet(true);
    this.accountId = accountId;
    setAccountIdIsSet(true);
    this.target = target;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RemoveStatusFromFilter(RemoveStatusFromFilter other) {
    __isset_bitfield = other.__isset_bitfield;
    this.filterId = other.filterId;
    this.accountId = other.accountId;
    if (other.isSetTarget()) {
      this.target = new StatusPointer(other.target);
    }
  }

  @Override
  public RemoveStatusFromFilter deepCopy() {
    return new RemoveStatusFromFilter(this);
  }

  @Override
  public void clear() {
    setFilterIdIsSet(false);
    this.filterId = 0;
    setAccountIdIsSet(false);
    this.accountId = 0;
    this.target = null;
  }

  public long getFilterId() {
    return this.filterId;
  }

  public RemoveStatusFromFilter setFilterId(long filterId) {
    this.filterId = filterId;
    setFilterIdIsSet(true);
    return this;
  }

  public void unsetFilterId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __FILTERID_ISSET_ID);
  }

  /** Returns true if field filterId is set (has been assigned a value) and false otherwise */
  public boolean isSetFilterId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __FILTERID_ISSET_ID);
  }

  public void setFilterIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __FILTERID_ISSET_ID, value);
  }

  public long getAccountId() {
    return this.accountId;
  }

  public RemoveStatusFromFilter setAccountId(long accountId) {
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

  @org.apache.thrift.annotation.Nullable
  public StatusPointer getTarget() {
    return this.target;
  }

  public RemoveStatusFromFilter setTarget(@org.apache.thrift.annotation.Nullable StatusPointer target) {
    this.target = target;
    return this;
  }

  public void unsetTarget() {
    this.target = null;
  }

  /** Returns true if field target is set (has been assigned a value) and false otherwise */
  public boolean isSetTarget() {
    return this.target != null;
  }

  public void setTargetIsSet(boolean value) {
    if (!value) {
      this.target = null;
    }
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case FILTER_ID:
      if (value == null) {
        unsetFilterId();
      } else {
        setFilterId((java.lang.Long)value);
      }
      break;

    case ACCOUNT_ID:
      if (value == null) {
        unsetAccountId();
      } else {
        setAccountId((java.lang.Long)value);
      }
      break;

    case TARGET:
      if (value == null) {
        unsetTarget();
      } else {
        setTarget((StatusPointer)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case FILTER_ID:
      return getFilterId();

    case ACCOUNT_ID:
      return getAccountId();

    case TARGET:
      return getTarget();

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
    case FILTER_ID:
      return isSetFilterId();
    case ACCOUNT_ID:
      return isSetAccountId();
    case TARGET:
      return isSetTarget();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof RemoveStatusFromFilter)
      return this.equals((RemoveStatusFromFilter)that);
    return false;
  }

  public boolean equals(RemoveStatusFromFilter that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_filterId = true;
    boolean that_present_filterId = true;
    if (this_present_filterId || that_present_filterId) {
      if (!(this_present_filterId && that_present_filterId))
        return false;
      if (this.filterId != that.filterId)
        return false;
    }

    boolean this_present_accountId = true;
    boolean that_present_accountId = true;
    if (this_present_accountId || that_present_accountId) {
      if (!(this_present_accountId && that_present_accountId))
        return false;
      if (this.accountId != that.accountId)
        return false;
    }

    boolean this_present_target = true && this.isSetTarget();
    boolean that_present_target = true && that.isSetTarget();
    if (this_present_target || that_present_target) {
      if (!(this_present_target && that_present_target))
        return false;
      if (!this.target.equals(that.target))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(filterId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(accountId);

    hashCode = hashCode * 8191 + ((isSetTarget()) ? 131071 : 524287);
    if (isSetTarget())
      hashCode = hashCode * 8191 + target.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(RemoveStatusFromFilter other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetFilterId(), other.isSetFilterId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetFilterId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.filterId, other.filterId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
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
    lastComparison = java.lang.Boolean.compare(isSetTarget(), other.isSetTarget());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTarget()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.target, other.target);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("RemoveStatusFromFilter(");
    boolean first = true;

    sb.append("filterId:");
    sb.append(this.filterId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("accountId:");
    sb.append(this.accountId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("target:");
    if (this.target == null) {
      sb.append("null");
    } else {
      sb.append(this.target);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'filterId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'accountId' because it's a primitive and you chose the non-beans generator.
    if (target == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'target' was not present! Struct: " + toString());
    }
    // check for sub-struct validity
    if (target != null) {
      target.validate();
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

  private static class RemoveStatusFromFilterStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveStatusFromFilterStandardScheme getScheme() {
      return new RemoveStatusFromFilterStandardScheme();
    }
  }

  private static class RemoveStatusFromFilterStandardScheme extends org.apache.thrift.scheme.StandardScheme<RemoveStatusFromFilter> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, RemoveStatusFromFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // FILTER_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.filterId = iprot.readI64();
              struct.setFilterIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // ACCOUNT_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.accountId = iprot.readI64();
              struct.setAccountIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // TARGET
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.target = new StatusPointer();
              struct.target.read(iprot);
              struct.setTargetIsSet(true);
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
      if (!struct.isSetFilterId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'filterId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetAccountId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'accountId' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, RemoveStatusFromFilter struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(FILTER_ID_FIELD_DESC);
      oprot.writeI64(struct.filterId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(ACCOUNT_ID_FIELD_DESC);
      oprot.writeI64(struct.accountId);
      oprot.writeFieldEnd();
      if (struct.target != null) {
        oprot.writeFieldBegin(TARGET_FIELD_DESC);
        struct.target.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class RemoveStatusFromFilterTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveStatusFromFilterTupleScheme getScheme() {
      return new RemoveStatusFromFilterTupleScheme();
    }
  }

  private static class RemoveStatusFromFilterTupleScheme extends org.apache.thrift.scheme.TupleScheme<RemoveStatusFromFilter> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RemoveStatusFromFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.filterId);
      oprot.writeI64(struct.accountId);
      struct.target.write(oprot);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RemoveStatusFromFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.filterId = iprot.readI64();
      struct.setFilterIdIsSet(true);
      struct.accountId = iprot.readI64();
      struct.setAccountIdIsSet(true);
      struct.target = new StatusPointer();
      struct.target.read(iprot);
      struct.setTargetIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

