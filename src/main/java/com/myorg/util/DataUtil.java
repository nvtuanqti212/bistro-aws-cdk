package com.myorg.util;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/13/2024, Monday
 * @description:
 **/
public class DataUtil {
    public static void requireNonEmptyOrNull(String param, String message){
        if (param == null || param.isBlank()){
            throw new IllegalArgumentException(message);
        }
    }
}
