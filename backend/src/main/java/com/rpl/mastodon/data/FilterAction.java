/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.rpl.mastodon.data;


public enum FilterAction implements org.apache.thrift.TEnum {
  Warn(1),
  Hide(2);

  private final int value;

  private FilterAction(int value) {
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
  public static FilterAction findByValue(int value) { 
    switch (value) {
      case 1:
        return Warn;
      case 2:
        return Hide;
      default:
        return null;
    }
  }
}