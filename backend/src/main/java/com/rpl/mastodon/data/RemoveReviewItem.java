/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class RemoveReviewItem implements org.apache.thrift.TBase<RemoveReviewItem, RemoveReviewItem._Fields>, java.io.Serializable, Cloneable, Comparable<RemoveReviewItem> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RemoveReviewItem");

  private static final org.apache.thrift.protocol.TField ITEM_FIELD_DESC = new org.apache.thrift.protocol.TField("item", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new RemoveReviewItemStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new RemoveReviewItemTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.lang.String item; // required
  public long timestamp; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ITEM((short)1, "item"),
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
        case 1: // ITEM
          return ITEM;
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
    tmpMap.put(_Fields.ITEM, new org.apache.thrift.meta_data.FieldMetaData("item", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RemoveReviewItem.class, metaDataMap);
  }

  public RemoveReviewItem() {
  }

  public RemoveReviewItem(
    java.lang.String item,
    long timestamp)
  {
    this();
    this.item = item;
    this.timestamp = timestamp;
    setTimestampIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RemoveReviewItem(RemoveReviewItem other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetItem()) {
      this.item = other.item;
    }
    this.timestamp = other.timestamp;
  }

  @Override
  public RemoveReviewItem deepCopy() {
    return new RemoveReviewItem(this);
  }

  @Override
  public void clear() {
    this.item = null;
    setTimestampIsSet(false);
    this.timestamp = 0;
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getItem() {
    return this.item;
  }

  public RemoveReviewItem setItem(@org.apache.thrift.annotation.Nullable java.lang.String item) {
    this.item = item;
    return this;
  }

  public void unsetItem() {
    this.item = null;
  }

  /** Returns true if field item is set (has been assigned a value) and false otherwise */
  public boolean isSetItem() {
    return this.item != null;
  }

  public void setItemIsSet(boolean value) {
    if (!value) {
      this.item = null;
    }
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public RemoveReviewItem setTimestamp(long timestamp) {
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
    case ITEM:
      if (value == null) {
        unsetItem();
      } else {
        setItem((java.lang.String)value);
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
    case ITEM:
      return getItem();

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
    case ITEM:
      return isSetItem();
    case TIMESTAMP:
      return isSetTimestamp();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof RemoveReviewItem)
      return this.equals((RemoveReviewItem)that);
    return false;
  }

  public boolean equals(RemoveReviewItem that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_item = true && this.isSetItem();
    boolean that_present_item = true && that.isSetItem();
    if (this_present_item || that_present_item) {
      if (!(this_present_item && that_present_item))
        return false;
      if (!this.item.equals(that.item))
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

    hashCode = hashCode * 8191 + ((isSetItem()) ? 131071 : 524287);
    if (isSetItem())
      hashCode = hashCode * 8191 + item.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    return hashCode;
  }

  @Override
  public int compareTo(RemoveReviewItem other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetItem(), other.isSetItem());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetItem()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.item, other.item);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("RemoveReviewItem(");
    boolean first = true;

    sb.append("item:");
    if (this.item == null) {
      sb.append("null");
    } else {
      sb.append(this.item);
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
    if (item == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'item' was not present! Struct: " + toString());
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

  private static class RemoveReviewItemStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveReviewItemStandardScheme getScheme() {
      return new RemoveReviewItemStandardScheme();
    }
  }

  private static class RemoveReviewItemStandardScheme extends org.apache.thrift.scheme.StandardScheme<RemoveReviewItem> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, RemoveReviewItem struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // ITEM
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.item = iprot.readString();
              struct.setItemIsSet(true);
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
    public void write(org.apache.thrift.protocol.TProtocol oprot, RemoveReviewItem struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.item != null) {
        oprot.writeFieldBegin(ITEM_FIELD_DESC);
        oprot.writeString(struct.item);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(struct.timestamp);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class RemoveReviewItemTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public RemoveReviewItemTupleScheme getScheme() {
      return new RemoveReviewItemTupleScheme();
    }
  }

  private static class RemoveReviewItemTupleScheme extends org.apache.thrift.scheme.TupleScheme<RemoveReviewItem> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RemoveReviewItem struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeString(struct.item);
      oprot.writeI64(struct.timestamp);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RemoveReviewItem struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.item = iprot.readString();
      struct.setItemIsSet(true);
      struct.timestamp = iprot.readI64();
      struct.setTimestampIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

