/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class AccountListWithId implements org.apache.thrift.TBase<AccountListWithId, AccountListWithId._Fields>, java.io.Serializable, Cloneable, Comparable<AccountListWithId> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("AccountListWithId");

  private static final org.apache.thrift.protocol.TField LIST_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("listId", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField ACCOUNT_LIST_FIELD_DESC = new org.apache.thrift.protocol.TField("accountList", org.apache.thrift.protocol.TType.STRUCT, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new AccountListWithIdStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new AccountListWithIdTupleSchemeFactory();

  public long listId; // required
  public @org.apache.thrift.annotation.Nullable AccountList accountList; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    LIST_ID((short)1, "listId"),
    ACCOUNT_LIST((short)2, "accountList");

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
        case 1: // LIST_ID
          return LIST_ID;
        case 2: // ACCOUNT_LIST
          return ACCOUNT_LIST;
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
  private static final int __LISTID_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.LIST_ID, new org.apache.thrift.meta_data.FieldMetaData("listId", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64        , "ListId")));
    tmpMap.put(_Fields.ACCOUNT_LIST, new org.apache.thrift.meta_data.FieldMetaData("accountList", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, AccountList.class)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(AccountListWithId.class, metaDataMap);
  }

  public AccountListWithId() {
  }

  public AccountListWithId(
    long listId,
    AccountList accountList)
  {
    this();
    this.listId = listId;
    setListIdIsSet(true);
    this.accountList = accountList;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public AccountListWithId(AccountListWithId other) {
    __isset_bitfield = other.__isset_bitfield;
    this.listId = other.listId;
    if (other.isSetAccountList()) {
      this.accountList = new AccountList(other.accountList);
    }
  }

  @Override
  public AccountListWithId deepCopy() {
    return new AccountListWithId(this);
  }

  @Override
  public void clear() {
    setListIdIsSet(false);
    this.listId = 0;
    this.accountList = null;
  }

  public long getListId() {
    return this.listId;
  }

  public AccountListWithId setListId(long listId) {
    this.listId = listId;
    setListIdIsSet(true);
    return this;
  }

  public void unsetListId() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __LISTID_ISSET_ID);
  }

  /** Returns true if field listId is set (has been assigned a value) and false otherwise */
  public boolean isSetListId() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __LISTID_ISSET_ID);
  }

  public void setListIdIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __LISTID_ISSET_ID, value);
  }

  @org.apache.thrift.annotation.Nullable
  public AccountList getAccountList() {
    return this.accountList;
  }

  public AccountListWithId setAccountList(@org.apache.thrift.annotation.Nullable AccountList accountList) {
    this.accountList = accountList;
    return this;
  }

  public void unsetAccountList() {
    this.accountList = null;
  }

  /** Returns true if field accountList is set (has been assigned a value) and false otherwise */
  public boolean isSetAccountList() {
    return this.accountList != null;
  }

  public void setAccountListIsSet(boolean value) {
    if (!value) {
      this.accountList = null;
    }
  }

  @Override
  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case LIST_ID:
      if (value == null) {
        unsetListId();
      } else {
        setListId((java.lang.Long)value);
      }
      break;

    case ACCOUNT_LIST:
      if (value == null) {
        unsetAccountList();
      } else {
        setAccountList((AccountList)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  @Override
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case LIST_ID:
      return getListId();

    case ACCOUNT_LIST:
      return getAccountList();

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
    case LIST_ID:
      return isSetListId();
    case ACCOUNT_LIST:
      return isSetAccountList();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof AccountListWithId)
      return this.equals((AccountListWithId)that);
    return false;
  }

  public boolean equals(AccountListWithId that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_listId = true;
    boolean that_present_listId = true;
    if (this_present_listId || that_present_listId) {
      if (!(this_present_listId && that_present_listId))
        return false;
      if (this.listId != that.listId)
        return false;
    }

    boolean this_present_accountList = true && this.isSetAccountList();
    boolean that_present_accountList = true && that.isSetAccountList();
    if (this_present_accountList || that_present_accountList) {
      if (!(this_present_accountList && that_present_accountList))
        return false;
      if (!this.accountList.equals(that.accountList))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(listId);

    hashCode = hashCode * 8191 + ((isSetAccountList()) ? 131071 : 524287);
    if (isSetAccountList())
      hashCode = hashCode * 8191 + accountList.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(AccountListWithId other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetListId(), other.isSetListId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetListId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.listId, other.listId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetAccountList(), other.isSetAccountList());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetAccountList()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.accountList, other.accountList);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("AccountListWithId(");
    boolean first = true;

    sb.append("listId:");
    sb.append(this.listId);
    first = false;
    if (!first) sb.append(", ");
    sb.append("accountList:");
    if (this.accountList == null) {
      sb.append("null");
    } else {
      sb.append(this.accountList);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'listId' because it's a primitive and you chose the non-beans generator.
    if (accountList == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'accountList' was not present! Struct: " + toString());
    }
    // check for sub-struct validity
    if (accountList != null) {
      accountList.validate();
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

  private static class AccountListWithIdStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public AccountListWithIdStandardScheme getScheme() {
      return new AccountListWithIdStandardScheme();
    }
  }

  private static class AccountListWithIdStandardScheme extends org.apache.thrift.scheme.StandardScheme<AccountListWithId> {

    @Override
    public void read(org.apache.thrift.protocol.TProtocol iprot, AccountListWithId struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // LIST_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.listId = iprot.readI64();
              struct.setListIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // ACCOUNT_LIST
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.accountList = new AccountList();
              struct.accountList.read(iprot);
              struct.setAccountListIsSet(true);
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
      if (!struct.isSetListId()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'listId' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    @Override
    public void write(org.apache.thrift.protocol.TProtocol oprot, AccountListWithId struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(LIST_ID_FIELD_DESC);
      oprot.writeI64(struct.listId);
      oprot.writeFieldEnd();
      if (struct.accountList != null) {
        oprot.writeFieldBegin(ACCOUNT_LIST_FIELD_DESC);
        struct.accountList.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class AccountListWithIdTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    @Override
    public AccountListWithIdTupleScheme getScheme() {
      return new AccountListWithIdTupleScheme();
    }
  }

  private static class AccountListWithIdTupleScheme extends org.apache.thrift.scheme.TupleScheme<AccountListWithId> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, AccountListWithId struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeI64(struct.listId);
      struct.accountList.write(oprot);
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, AccountListWithId struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.listId = iprot.readI64();
      struct.setListIdIsSet(true);
      struct.accountList = new AccountList();
      struct.accountList.read(iprot);
      struct.setAccountListIsSet(true);
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

