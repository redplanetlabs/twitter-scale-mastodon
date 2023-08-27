package com.rpl.mastodon.ops;

import com.rpl.rama.ops.RamaFunction1;
import org.apache.thrift.*;

import static com.rpl.mastodon.MastodonHelpers.*;

public class ExtractField implements RamaFunction1<TBase, Object> {
  String _fieldName;

  public ExtractField(String name) {
    _fieldName = name;
  }

  @Override
  public Object invoke(TBase obj) {
    return getTFieldByName(obj, _fieldName);
  }
}
