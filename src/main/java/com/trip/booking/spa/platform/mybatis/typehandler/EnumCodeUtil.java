package com.trip.booking.spa.platform.mybatis.typehandler;

import java.lang.reflect.Method;

/**
 *  Created by by zhe.hao
 */
public class EnumCodeUtil {
    public static <T extends Enum<T>> T getEnumByCode(int code, Class clazz) {
        try {
            Method method = clazz.getDeclaredMethod("values");
            Enum[] values = (Enum[]) method.invoke(null);
            for (Enum v : values) {
                int eCode = getCode(v);
                if (eCode == code) {
                    return (T) v;
                }
            }
            throw new RuntimeException("no match enum for code " + code);
        } catch (Throwable e) {
            throw new RuntimeException("invoke values method error", e);
        }
    }

    public static int getCode(Object type) {
        try {
            Method codeMethod = type.getClass().getDeclaredMethod("getCode");
            Integer result = (Integer) codeMethod.invoke(type, new Object[0]);
            return result.intValue();
        } catch (Throwable e) {
            throw new RuntimeException("invoke values method error", e);
        }
    }
}
