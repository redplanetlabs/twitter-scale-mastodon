/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class KeywordFilter implements org.apache.thrift.TBase<KeywordFilter, KeywordFilter._Fields>, java.io.Serializable, Cloneable, Comparable<KeywordFilter> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("KeywordFilter");

  private static final org.apache.thrift.protocol.TField WORD_FIELD_DESC = new org.apache.thrift.protocol.TField("word", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField WHOLE_WORD_FIELD_DESC = new org.apache.thrift.protocol.TField("wholeWord", org.apache.thrift.protocol.TType.BOOL, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new KeywordFilterStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new KeywordFilterTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.lang.String word; // required
  public boolean wholeWord; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    WORD((short)1, "word"),
    WHOLE_WORD((short)2, "wholeWord");

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
        case 1: // WORD
          return WORD;
        case 2: // WHOLE_WORD
          return WHOLE_WORD;
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
  private static final int __WHOLEWORD_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.WORD, new org.apache.thrift.meta_data.FieldMetaData("word", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.WHOLE_WORD, new org.apache.thrift.meta_data.FieldMetaData("wholeWord", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(KeywordFilter.class, metaDataMap);
  }

  public KeywordFilter() {
  }

  public KeywordFilter(
    java.lang.String word,
    boolean wholeWord)
  {
    this();
    this.word = word;
    this.wholeWord = wholeWord;
    setWholeWordIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public KeywordFilter(KeywordFilter other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetWord()) {
      this.word = other.word;
    }
    this.wholeWord = other.wholeWord;
  }

  @Override
  public KeywordFilter deepCopy() {
    return new KeywordFilter(this);
  }

  @Override
  public void clear() {
    this.word = null;
    setWholeWordIsSet(false);
    this.wholeWord = false;
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.String getWord() {
    return this.word;
  }

  public KeywordFilter setWord(@org.apache.thrift.annotation.Nullable java.lang.String word) {
    this.word = word;
    return this;
  }

  public void unsetWord() {
    this.word = null;
  }

  /** Returns true if field word is set (has been assigned a value) and false otherwise */
  public boolean isSetWord() {
    return this.word != null;
  }

  public void setWordIsSet(boolean value) {
    if (!value) {
      this.word = null;
    }
  }

  public boolean isWholeWord() {
    return this.wholeWord;
  }

  public KeywordFilter setWholeWord(boolean wholeWord) {
    this.wholeWord = wholeWord;
    setWholeWordIsSet(true);
    return this;
  }

  public void unsetWholeWord() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __WHOLEWORD_ISSET_ID);
  }

  /** Returns true if field wholeWord is set (has been assigned a value) and false otherwise */
  public boolean isSetWholeWord() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __WHOLEWORD_ISSET_ID);
  }

  public void setWholeWordIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __WHOLEWORD_ISSET_ID, value);
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case WORD:
      if (value == null) {
        unsetWord();
      } else {
        setWord((java.lang.String)value);
      }
      break;

    case WHOLE_WORD:
      if (value == null) {
        unsetWholeWord();
      } else {
        setWholeWord((java.lang.Boolean)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case WORD:
      return getWord();

    case WHOLE_WORD:
      return isWholeWord();

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
    case WORD:
      return isSetWord();
    case WHOLE_WORD:
      return isSetWholeWord();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof KeywordFilter)
      return this.equals((KeywordFilter)that);
    return false;
  }

  public boolean equals(KeywordFilter that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_word = true && this.isSetWord();
    boolean that_present_word = true && that.isSetWord();
    if (this_present_word || that_present_word) {
      if (!(this_present_word && that_present_word))
        return false;
      if (!this.word.equals(that.word))
        return false;
    }

    boolean this_present_wholeWord = true;
    boolean that_present_wholeWord = true;
    if (this_present_wholeWord || that_present_wholeWord) {
      if (!(this_present_wholeWord && that_present_wholeWord))
        return false;
      if (this.wholeWord != that.wholeWord)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetWord()) ? 131071 : 524287);
    if (isSetWord())
      hashCode = hashCode * 8191 + word.hashCode();

    hashCode = hashCode * 8191 + ((wholeWord) ? 131071 : 524287);

    return hashCode;
  }

  @Override
  public int compareTo(KeywordFilter other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetWord(), other.isSetWord());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetWord()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.word, other.word);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetWholeWord(), other.isSetWholeWord());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetWholeWord()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.wholeWord, other.wholeWord);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("KeywordFilter(");
    boolean first = true;

    sb.append("word:");
    if (this.word == null) {
      sb.append("null");
    } else {
      sb.append(this.word);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("wholeWord:");
    sb.append(this.wholeWord);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (word == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'word' was not present! Struct: " + toString());
    }
    // alas, we cannot check 'wholeWord' because it's a primitive and you chose the non-beans generator.
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

  private static class KeywordFilterStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public KeywordFilterStandardScheme getScheme() {
      return new KeywordFilterStandardScheme();
    }
  }

  private static class KeywordFilterStandardScheme extends org.apache.thrift.scheme.StandardScheme<KeywordFilter> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, KeywordFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // WORD
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.word = iprot.readString();
              struct.setWordIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // WHOLE_WORD
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.wholeWord = iprot.readBool();
              struct.setWholeWordIsSet(true);
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
      if (!struct.isSetWholeWord()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'wholeWord' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, KeywordFilter struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.word != null) {
        oprot.writeFieldBegin(WORD_FIELD_DESC);
        oprot.writeString(struct.word);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(WHOLE_WORD_FIELD_DESC);
      oprot.writeBool(struct.wholeWord);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class KeywordFilterTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public KeywordFilterTupleScheme getScheme() {
      return new KeywordFilterTupleScheme();
    }
  }

  private static class KeywordFilterTupleScheme extends org.apache.thrift.scheme.TupleScheme<KeywordFilter> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, KeywordFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeString(struct.word);
      oprot.writeBool(struct.wholeWord);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, KeywordFilter struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.word = iprot.readString();
      struct.setWordIsSet(true);
      struct.wholeWord = iprot.readBool();
      struct.setWholeWordIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

