package com.rpl.mastodon.serialization;

import com.rpl.rama.RamaCustomSerialization;
import org.apache.thrift.*;
import org.apache.thrift.protocol.TCompactProtocol;
import java.io.*;
import java.util.*;

public abstract class ThriftSerialization implements RamaCustomSerialization<TBase> {
  private final Map<Byte, Class> _idToType = new HashMap<>();
  private final Map<Class, Byte> _typeToId = new HashMap<>();

  protected ThriftSerialization() {
    Map<Integer, Class> m = typeIds();
    for(Integer id: m.keySet()) _idToType.put(id.byteValue(), m.get(id));
    for(byte id: _idToType.keySet()) _typeToId.put(_idToType.get(id), id);
  }

  @Override
  public void serialize(TBase obj, DataOutput out) throws Exception {
    Byte id = _typeToId.get(obj.getClass());
    if(id==null) throw new RuntimeException("Could not find type id for " + obj.getClass());
    out.writeByte(id);
    byte[] serialized = new TSerializer(new TCompactProtocol.Factory()).serialize(obj);
    out.writeInt(serialized.length);
    out.write(serialized);
  }

  @Override
  public TBase deserialize(DataInput in) throws Exception {
    TBase obj = (TBase) _idToType.get(in.readByte()).newInstance();
    byte[] arr = new byte[in.readInt()];
    in.readFully(arr);
    new TDeserializer(new TCompactProtocol.Factory()).deserialize(obj, arr);
    return obj;
  }

  @Override
  public Class targetType() {
    return TBase.class;
  }

  protected abstract Map<Integer, Class> typeIds();
}
