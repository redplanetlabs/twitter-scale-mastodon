/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;


public enum FilterContext implements org.apache.thrift.TEnum {
  Home(1),
  Notifications(2),
  Public(3),
  Thread(4),
  Account(5);

  private final int value;

  private FilterContext(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  @Override
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  @org.apache.thrift.annotation.Nullable
  public static FilterContext findByValue(int value) { 
    switch (value) {
      case 1:
        return Home;
      case 2:
        return Notifications;
      case 3:
        return Public;
      case 4:
        return Thread;
      case 5:
        return Account;
      default:
        return null;
    }
  }
}
