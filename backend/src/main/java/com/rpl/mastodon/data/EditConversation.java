/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class EditConversation implements org.apache.thrift.TBase<EditConversation, EditConversation._Fields>, java.io.Serializable, Cloneable, Comparable<EditConversation> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("EditConversation");

  private static final org.apache.thrift.protocol.TField ACCOUNT_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("accountId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField CONVERSATION_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("conversationId", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField UNREAD_FIELD_DESC = new org.apache.thrift.protocol.TField("unread", org.apache.thrift.protocol.TType.BOOL, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new EditConversationStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new EditConversationTupleSchemeFactory();

  public long accountId; // required
  public long conversationId; // required
  public boolean unread; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ACCOUNT_ID((short)1, "accountId"),
    CONVERSATION_ID((short)2, "conversationId"),
    UNREAD((short)3, "unread");

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
        case 2: // CONVERSATION_ID
          return CONVERSATION_ID;
        case 3: // UNREAD
          return UNREAD;
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
  private static final int __CONVERSATIONID_ISSET_ID = 1;
  private static final int __UNREAD_ISSET_ID = 2;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.ACCOUNT_ID, new org.apache.thrift.meta_data.FieldMetaData("accountId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "AccountId")));
    tmpMap.put(_Fields.CONVERSATION_ID, new org.apache.thrift.meta_data.FieldMetaData("conversationId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "ConversationId")));
    tmpMap.put(_Fields.UNREAD, new org.apache.thrift.meta_data.FieldMetaData("unread", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(EditConversation.class, metaDataMap);
  }

  public EditConversation() {
  }

  public EditConversation(
    long accountId,
    long conversationId,
    boolean unread)
  {
    this();
    this.accountId = accountId;
    setAccountIdIsSet(true);
    this.conversationId = conversationId;
    setConversationIdIsSet(true);
    this.unread = unread;
    setUnreadIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public EditConversation(EditConversation other) {
    __isset_bitfield = other.__isset_bitfield;
    this.accountId = other.accountId;
    this.conversationId = other.conversationId;
    this.unread = other.unread;
  }

  @Override
  public EditConversation deepCopy() {
    return new EditConversation(this);
  }

  @Override
  public void clear() {
    setAccountIdIsSet(false);
    this.accountId = 0;
    setConversationIdIsSet(false);
    this.conversationId = 0;
    setUnreadIsSet(false);
    this.unread = false;
  }

  public long getAccountId() {
    return this.accountId;
  }

  public EditConversation setAccountId(long accountId) {
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

  public long getConversationId() {
    return this.conversationId;
  }

  public EditConversation setConversationId(long conversationId) {
    this.conversationId = conversationId;
    setConversationIdIsSet(true);
    return this;
  }

  public void unsetConversationId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __CONVERSATIONID_ISSET_ID);
  }

  /** Returns true if field conversationId is set (has been assigned a value) and false otherwise */
  public boolean isSetConversationId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __CONVERSATIONID_ISSET_ID);
  }

  public void setConversationIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __CONVERSATIONID_ISSET_ID, value);
  }

  public boolean isUnread() {
    return this.unread;
  }

  public EditConversation setUnread(boolean unread) {
    this.unread = unread;
    setUnreadIsSet(true);
    return this;
  }

  public void unsetUnread() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __UNREAD_ISSET_ID);
  }

  /** Returns true if field unread is set (has been assigned a value) and false otherwise */
  public boolean isSetUnread() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __UNREAD_ISSET_ID);
  }

  public void setUnreadIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __UNREAD_ISSET_ID, value);
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

    case CONVERSATION_ID:
      if (value == null) {
        unsetConversationId();
      } else {
        setConversationId((java.lang.Long)value);
      }
      break;

    case UNREAD:
      if (value == null) {
        unsetUnread();
      } else {
        setUnread((java.lang.Boolean)value);
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

    case CONVERSATION_ID:
      return getConversationId();

    case UNREAD:
      return isUnread();

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
    case CONVERSATION_ID:
      return isSetConversationId();
    case UNREAD:
      return isSetUnread();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof EditConversation)
      return this.equals((EditConversation)that);
    return false;
  }

  public boolean equals(EditConversation that) {
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

    boolean this_present_conversationId = true;
    boolean that_present_conversationId = true;
    if (this_present_conversationId || that_present_conversationId) {
      if (!(this_present_conversationId && that_present_conversationId))
        return false;
      if (this.conversationId != that.conversationId)
        return false;
    }

    boolean this_present_unread = true;
    boolean that_present_unread = true;
    if (this_present_unread || that_present_unread) {
      if (!(this_present_unread && that_present_unread))
        return false;
      if (this.unread != that.unread)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(accountId);

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(conversationId);

    hashCode = hashCode * 8191 + ((unread) ? 131071 : 524287);

    return hashCode;
  }

  @Override
  public int compareTo(EditConversation other) {
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
    lastComparison = java.lang.Boolean.compare(isSetConversationId(), other.isSetConversationId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetConversationId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.conversationId, other.conversationId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetUnread(), other.isSetUnread());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetUnread()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.unread, other.unread);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("EditConversation(");
    boolean first = true;

    sb.append("accountId:");
    sb.append(this.accountId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("conversationId:");
    sb.append(this.conversationId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("unread:");
    sb.append(this.unread);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'accountId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'conversationId' because it's a primitive and you chose the non-beans generator.
    // alas, we cannot check 'unread' because it's a primitive and you chose the non-beans generator.
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

  private static class EditConversationStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public EditConversationStandardScheme getScheme() {
      return new EditConversationStandardScheme();
    }
  }

  private static class EditConversationStandardScheme extends org.apache.thrift.scheme.StandardScheme<EditConversation> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, EditConversation struct) throws org.apache.thrift.TException {
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
          case 2: // CONVERSATION_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.conversationId = iprot.readI64();
              struct.setConversationIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // UNREAD
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.unread = iprot.readBool();
              struct.setUnreadIsSet(true);
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
      if (!struct.isSetConversationId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'conversationId' was not found in serialized data! Struct: " + toString());
      }
      if (!struct.isSetUnread()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'unread' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, EditConversation struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(ACCOUNT_ID_FIELD_DESC);
      oprot.writeI64(struct.accountId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(CONVERSATION_ID_FIELD_DESC);
      oprot.writeI64(struct.conversationId);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(UNREAD_FIELD_DESC);
      oprot.writeBool(struct.unread);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class EditConversationTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public EditConversationTupleScheme getScheme() {
      return new EditConversationTupleScheme();
    }
  }

  private static class EditConversationTupleScheme extends org.apache.thrift.scheme.TupleScheme<EditConversation> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, EditConversation struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.accountId);
      oprot.writeI64(struct.conversationId);
      oprot.writeBool(struct.unread);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, EditConversation struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.accountId = iprot.readI64();
      struct.setAccountIdIsSet(true);
      struct.conversationId = iprot.readI64();
      struct.setConversationIdIsSet(true);
      struct.unread = iprot.readBool();
      struct.setUnreadIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

