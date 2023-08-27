/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class ListFanout implements org.apache.thrift.TBase<ListFanout, ListFanout._Fields>, java.io.Serializable, Cloneable, Comparable<ListFanout> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("ListFanout");

  private static final org.apache.thrift.protocol.TField AUTHOR_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("authorId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField NEXT_INDEX_FIELD_DESC = new org.apache.thrift.protocol.TField("nextIndex", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField STATUS_FIELD_DESC = new org.apache.thrift.protocol.TField("status", org.apache.thrift.protocol.TType.STRUCT, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new ListFanoutStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new ListFanoutTupleSchemeFactory();

  public long authorId; // required
  public long nextIndex; // required
  public @org.apache.thrift.annotation.Nullable Status status; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    AUTHOR_ID((short)1, "authorId"),
    NEXT_INDEX((short)2, "nextIndex"),
    STATUS((short)3, "status");

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
        case 2: // NEXT_INDEX
          return NEXT_INDEX;
        case 3: // STATUS
          return STATUS;
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
  private static final int __NEXTINDEX_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.AUTHOR_ID, new org.apache.thrift.meta_data.FieldMetaData("authorId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.NEXT_INDEX, new org.apache.thrift.meta_data.FieldMetaData("nextIndex", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.STATUS, new org.apache.thrift.meta_data.FieldMetaData("status", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, Status.class)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(ListFanout.class, metaDataMap);
  }

  public ListFanout() {
  }

  public ListFanout(
    long authorId,
    long nextIndex,
    Status status)
  {
    this();
    this.authorId = authorId;
    setAuthorIdIsSet(true);
    this.nextIndex = nextIndex;
    setNextIndexIsSet(true);
    this.status = status;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public ListFanout(ListFanout other) {
    __isset_bitfield = other.__isset_bitfield;
    this.authorId = other.authorId;
    this.nextIndex = other.nextIndex;
    if (other.isSetStatus()) {
      this.status = new Status(other.status);
    }
  }

  @Override
  public ListFanout deepCopy() {
    return new ListFanout(this);
  }

  @Override
  public void clear() {
    setAuthorIdIsSet(false);
    this.authorId = 0;
    setNextIndexIsSet(false);
    this.nextIndex = 0;
    this.status = null;
  }

  public long getAuthorId() {
    return this.authorId;
  }

  public ListFanout setAuthorId(long authorId) {
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

  public long getNextIndex() {
    return this.nextIndex;
  }

  public ListFanout setNextIndex(long nextIndex) {
    this.nextIndex = nextIndex;
    setNextIndexIsSet(true);
    return this;
  }

  public void unsetNextIndex() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __NEXTINDEX_ISSET_ID);
  }

  /** Returns true if field nextIndex is set (has been assigned a value) and false otherwise */
  public boolean isSetNextIndex() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __NEXTINDEX_ISSET_ID);
  }

  public void setNextIndexIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __NEXTINDEX_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public Status getStatus() {
    return this.status;
  }

  public ListFanout setStatus(@org.apache.thrift.annotation.Nullable Status status) {
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

    case NEXT_INDEX:
      if (value == null) {
        unsetNextIndex();
      } else {
        setNextIndex((java.lang.Long)value);
      }
      break;

    case STATUS:
      if (value == null) {
        unsetStatus();
      } else {
        setStatus((Status)value);
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

    case NEXT_INDEX:
      return getNextIndex();

    case STATUS:
      return getStatus();

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
    case NEXT_INDEX:
      return isSetNextIndex();
    case STATUS:
      return isSetStatus();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof ListFanout)
      return this.equals((ListFanout)that);
    return false;
  }

  public boolean equals(ListFanout that) {
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

    boolean this_present_nextIndex = true;
    boolean that_present_nextIndex = true;
    if (this_present_nextIndex || that_present_nextIndex) {
      if (!(this_present_nextIndex && that_present_nextIndex))
        return false;
      if (this.nextIndex != that.nextIndex)
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

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(authorId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(nextIndex);

    hashCode = hashCode * 8191 + ((isSetStatus()) ? 131071 : 524287);
    if (isSetStatus())
      hashCode = hashCode * 8191 + status.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(ListFanout other) {
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
    lastComparison = java.lang.Boolean.compare(isSetNextIndex(), other.isSetNextIndex());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNextIndex()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.nextIndex, other.nextIndex);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("ListFanout(");
    boolean first = true;

    sb.append("authorId:");
    sb.append(this.authorId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("nextIndex:");
    sb.append(this.nextIndex);
    first = false;
    if (!first) sb.append(", ");
    sb.append("status:");
    if (this.status == null) {
      sb.append("null");
    } else {
      sb.append(this.status);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'authorId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'nextIndex' because it's a primitive and you chose the non-beans generator.
    if (status == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'status' was not present! Struct: " + toString());
    }
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

  private static class ListFanoutStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public ListFanoutStandardScheme getScheme() {
      return new ListFanoutStandardScheme();
    }
  }

  private static class ListFanoutStandardScheme extends org.apache.thrift.scheme.StandardScheme<ListFanout> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, ListFanout struct) throws org.apache.thrift.TException {
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
          case 2: // NEXT_INDEX
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.nextIndex = iprot.readI64();
              struct.setNextIndexIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // STATUS
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.status = new Status();
              struct.status.read(iprot);
              struct.setStatusIsSet(true);
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
      if (!struct.isSetNextIndex()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'nextIndex' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, ListFanout struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(AUTHOR_ID_FIELD_DESC);
      oprot.writeI64(struct.authorId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(NEXT_INDEX_FIELD_DESC);
      oprot.writeI64(struct.nextIndex);
      oprot.writeFieldEnd();
      if (struct.status != null) {
        oprot.writeFieldBegin(STATUS_FIELD_DESC);
        struct.status.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class ListFanoutTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public ListFanoutTupleScheme getScheme() {
      return new ListFanoutTupleScheme();
    }
  }

  private static class ListFanoutTupleScheme extends org.apache.thrift.scheme.TupleScheme<ListFanout> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, ListFanout struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.authorId);
      oprot.writeI64(struct.nextIndex);
      struct.status.write(oprot);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, ListFanout struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.authorId = iprot.readI64();
      struct.setAuthorIdIsSet(true);
      struct.nextIndex = iprot.readI64();
      struct.setNextIndexIsSet(true);
      struct.status = new Status();
      struct.status.read(iprot);
      struct.setStatusIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}
