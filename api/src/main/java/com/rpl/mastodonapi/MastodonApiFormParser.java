package com.rpl.mastodonapi;

import org.springframework.util.MultiValueMap;

import java.lang.reflect.Field;
import java.util.*;

public class MastodonApiFormParser {
    enum ParsableClass {
        String,
        Integer,
        Long,
        Boolean,
        List
    }

    public static <T> T parseParams(MultiValueMap<String, String> formParams, T params) {
        for (Map.Entry<String, List<String>> formParam : formParams.entrySet()) {
            String paramName = formParam.getKey();
            Field field = null;
            try {
                field = params.getClass().getField(paramName);
            } catch (NoSuchFieldException e) {
                System.err.printf("Expected '%s' to be a field in %s\n", paramName, params.getClass().getSimpleName());
                continue; // ignore and continue
            }
            List<String> vals = formParam.getValue();
            ParsableClass clazz = ParsableClass.valueOf(field.getType().getSimpleName());
            try {
                switch (clazz) {
                    case String:
                        field.set(params, vals.get(0));
                        break;
                    case Integer:
                        field.set(params, Integer.valueOf(vals.get(0)));
                        break;
                    case Long:
                        field.set(params, Long.valueOf(vals.get(0)));
                        break;
                    case Boolean:
                        field.set(params, Boolean.valueOf(vals.get(0)));
                        break;
                    case List:
                        field.set(params, vals);
                        break;
                }
            } catch (IllegalAccessException e) {
                System.err.printf("Failed to parse '%s' as a %s\n", paramName, clazz.name());
                throw new RuntimeException(e);
            }
        }
        return params;
    }
}
