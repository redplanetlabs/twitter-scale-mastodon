/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class PollContent implements org.apache.thrift.TBase<PollContent, PollContent._Fields>, java.io.Serializable, Cloneable, Comparable<PollContent> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("PollContent");

  private static final org.apache.thrift.protocol.TField CHOICES_FIELD_DESC = new org.apache.thrift.protocol.TField("choices", org.apache.thrift.protocol.TType.LIST, (short)1);
  private static final org.apache.thrift.protocol.TField EXPIRATION_MILLIS_FIELD_DESC = new org.apache.thrift.protocol.TField("expirationMillis", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField MULTIPLE_CHOICE_FIELD_DESC = new org.apache.thrift.protocol.TField("multipleChoice", org.apache.thrift.protocol.TType.BOOL, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new PollContentStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new PollContentTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.util.List<java.lang.String> choices; // required
  public long expirationMillis; // required
  public boolean multipleChoice; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    CHOICES((short)1, "choices"),
    EXPIRATION_MILLIS((short)2, "expirationMillis"),
    MULTIPLE_CHOICE((short)3, "multipleChoice");

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
        case 1: // CHOICES
          return CHOICES;
        case 2: // EXPIRATION_MILLIS
          return EXPIRATION_MILLIS;
        case 3: // MULTIPLE_CHOICE
          return MULTIPLE_CHOICE;
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
  private static final int __EXPIRATIONMILLIS_ISSET_ID = 0;
  private static final int __MULTIPLECHOICE_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.CHOICES, new org.apache.thrift.meta_data.FieldMetaData("choices", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING))));
    tmpMap.put(_Fields.EXPIRATION_MILLIS, new org.apache.thrift.meta_data.FieldMetaData("expirationMillis", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "Timestamp")));
    tmpMap.put(_Fields.MULTIPLE_CHOICE, new org.apache.thrift.meta_data.FieldMetaData("multipleChoice", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(PollContent.class, metaDataMap);
  }

  public PollContent() {
  }

  public PollContent(
    java.util.List<java.lang.String> choices,
    long expirationMillis,
    boolean multipleChoice)
  {
    this();
    this.choices = choices;
    this.expirationMillis = expirationMillis;
    setExpirationMillisIsSet(true);
    this.multipleChoice = multipleChoice;
    setMultipleChoiceIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public PollContent(PollContent other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetChoices()) {
      java.util.List<java.lang.String> __this__choices = new java.util.ArrayList<java.lang.String>(other.choices);
      this.choices = __this__choices;
    }
    this.expirationMillis = other.expirationMillis;
    this.multipleChoice = other.multipleChoice;
  }

  @Override
  public PollContent deepCopy() {
    return new PollContent(this);
  }

  @Override
  public void clear() {
    this.choices = null;
    setExpirationMillisIsSet(false);
    this.expirationMillis = 0;
    setMultipleChoiceIsSet(false);
    this.multipleChoice = false;
  }

  public int getChoicesSize() {
    return (this.choices == null) ? 0 : this.choices.size();
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.Iterator<java.lang.String> getChoicesIterator() {
    return (this.choices == null) ? null : this.choices.iterator();
  }

  public void addToChoices(java.lang.String elem) {
    if (this.choices == null) {
      this.choices = new java.util.ArrayList<java.lang.String>();
    }
    this.choices.add(elem);
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.List<java.lang.String> getChoices() {
    return this.choices;
  }

  public PollContent setChoices(@org.apache.thrift.annotation.Nullable java.util.List<java.lang.String> choices) {
    this.choices = choices;
    return this;
  }

  public void unsetChoices() {
    this.choices = null;
  }

  /** Returns true if field choices is set (has been assigned a value) and false otherwise */
  public boolean isSetChoices() {
    return this.choices != null;
  }

  public void setChoicesIsSet(boolean value) {
    if (!value) {
      this.choices = null;
    }
  }

  public long getExpirationMillis() {
    return this.expirationMillis;
  }

  public PollContent setExpirationMillis(long expirationMillis) {
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

  public boolean isMultipleChoice() {
    return this.multipleChoice;
  }

  public PollContent setMultipleChoice(boolean multipleChoice) {
    this.multipleChoice = multipleChoice;
    setMultipleChoiceIsSet(true);
    return this;
  }

  public void unsetMultipleChoice() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __MULTIPLECHOICE_ISSET_ID);
  }

  /** Returns true if field multipleChoice is set (has been assigned a value) and false otherwise */
  public boolean isSetMultipleChoice() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __MULTIPLECHOICE_ISSET_ID);
  }

  public void setMultipleChoiceIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __MULTIPLECHOICE_ISSET_ID, value);
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case CHOICES:
      if (value == null) {
        unsetChoices();
      } else {
        setChoices((java.util.List<java.lang.String>)value);
      }
      break;

    case EXPIRATION_MILLIS:
      if (value == null) {
        unsetExpirationMillis();
      } else {
        setExpirationMillis((java.lang.Long)value);
      }
      break;

    case MULTIPLE_CHOICE:
      if (value == null) {
        unsetMultipleChoice();
      } else {
        setMultipleChoice((java.lang.Boolean)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case CHOICES:
      return getChoices();

    case EXPIRATION_MILLIS:
      return getExpirationMillis();

    case MULTIPLE_CHOICE:
      return isMultipleChoice();

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
    case CHOICES:
      return isSetChoices();
    case EXPIRATION_MILLIS:
      return isSetExpirationMillis();
    case MULTIPLE_CHOICE:
      return isSetMultipleChoice();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof PollContent)
      return this.equals((PollContent)that);
    return false;
  }

  public boolean equals(PollContent that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_choices = true && this.isSetChoices();
    boolean that_present_choices = true && that.isSetChoices();
    if (this_present_choices || that_present_choices) {
      if (!(this_present_choices && that_present_choices))
        return false;
      if (!this.choices.equals(that.choices))
        return false;
    }

    boolean this_present_expirationMillis = true;
    boolean that_present_expirationMillis = true;
    if (this_present_expirationMillis || that_present_expirationMillis) {
      if (!(this_present_expirationMillis && that_present_expirationMillis))
        return false;
      if (this.expirationMillis != that.expirationMillis)
        return false;
    }

    boolean this_present_multipleChoice = true;
    boolean that_present_multipleChoice = true;
    if (this_present_multipleChoice || that_present_multipleChoice) {
      if (!(this_present_multipleChoice && that_present_multipleChoice))
        return false;
      if (this.multipleChoice != that.multipleChoice)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetChoices()) ? 131071 : 524287);
    if (isSetChoices())
      hashCode = hashCode * 8191 + choices.hashCode();

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(expirationMillis);

    hashCode = hashCode * 8191 + ((multipleChoice) ? 131071 : 524287);

    return hashCode;
  }

  @Override
  public int compareTo(PollContent other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetChoices(), other.isSetChoices());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetChoices()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.choices, other.choices);
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
    lastComparison = java.lang.Boolean.compare(isSetMultipleChoice(), other.isSetMultipleChoice());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMultipleChoice()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.multipleChoice, other.multipleChoice);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("PollContent(");
    boolean first = true;

    sb.append("choices:");
    if (this.choices == null) {
      sb.append("null");
    } else {
      sb.append(this.choices);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("expirationMillis:");
    sb.append(this.expirationMillis);
    first = false;
    if (!first) sb.append(", ");
    sb.append("multipleChoice:");
    sb.append(this.multipleChoice);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (choices == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'choices' was not present! Struct: " + toString());
    }
    // alas, we cannot check 'expirationMillis' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'multipleChoice' because it's a primitive and you chose the non-beans generator.
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

  private static class PollContentStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public PollContentStandardScheme getScheme() {
      return new PollContentStandardScheme();
    }
  }

  private static class PollContentStandardScheme extends org.apache.thrift.scheme.StandardScheme<PollContent> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, PollContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // CHOICES
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list96 = iprot.readListBegin();
                struct.choices = new java.util.ArrayList<java.lang.String>(_list96.size);
                @org.apache.thrift.annotation.Nullable java.lang.String _elem97;
                for (int _i98 = 0; _i98 < _list96.size; ++_i98)
                {
                  _elem97 = iprot.readString();
                  struct.choices.add(_elem97);
                }
                iprot.readListEnd();
              }
              struct.setChoicesIsSet(true);
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
          case 3: // MULTIPLE_CHOICE
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.multipleChoice = iprot.readBool();
              struct.setMultipleChoiceIsSet(true);
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
      if (!struct.isSetExpirationMillis()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'expirationMillis' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetMultipleChoice()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'multipleChoice' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, PollContent struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.choices != null) {
        oprot.writeFieldBegin(CHOICES_FIELD_DESC);
        {
          oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRING, struct.choices.size()));
          for (java.lang.String _iter99 : struct.choices)
          {
            oprot.writeString(_iter99);
          }
          oprot.writeListEnd();
        }
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(EXPIRATION_MILLIS_FIELD_DESC);
      oprot.writeI64(struct.expirationMillis);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(MULTIPLE_CHOICE_FIELD_DESC);
      oprot.writeBool(struct.multipleChoice);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class PollContentTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public PollContentTupleScheme getScheme() {
      return new PollContentTupleScheme();
    }
  }

  private static class PollContentTupleScheme extends org.apache.thrift.scheme.TupleScheme<PollContent> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, PollContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      {
        oprot.writeI32(struct.choices.size());
        for (java.lang.String _iter100 : struct.choices)
        {
          oprot.writeString(_iter100);
        }
      }
      oprot.writeI64(struct.expirationMillis);
      oprot.writeBool(struct.multipleChoice);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, PollContent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      {
        org.apache.thrift.protocol.TList _list101 = iprot.readListBegin(org.apache.thrift.protocol.TType.STRING);
        struct.choices = new java.util.ArrayList<java.lang.String>(_list101.size);
        @org.apache.thrift.annotation.Nullable java.lang.String _elem102;
        for (int _i103 = 0; _i103 < _list101.size; ++_i103)
        {
          _elem102 = iprot.readString();
          struct.choices.add(_elem102);
        }
      }
      struct.setChoicesIsSet(true);
      struct.expirationMillis = iprot.readI64();
      struct.setExpirationMillisIsSet(true);
      struct.multipleChoice = iprot.readBool();
      struct.setMultipleChoiceIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

