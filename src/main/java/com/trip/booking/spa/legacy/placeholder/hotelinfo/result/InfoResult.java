/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JavaType
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.ObjectMapper
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.result;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class InfoResult<T> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Integer status;
    private String msg;
    private T data;

    public InfoResult() {
    }

    public InfoResult(Integer status, String msg, T data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    public InfoResult(T data) {
        this.status = 200;
        this.msg = "SUCCESS";
        this.data = data;
    }

    public static <T> InfoResult build(Integer status, String msg, T data) {
        return new InfoResult<T>(status, msg, data);
    }

    public static <T> InfoResult<T> success(T data) {
        return new InfoResult<T>(data);
    }

    public static <T> InfoResult<T> ok() {
        return new InfoResult<T>(null);
    }

    public static <T> InfoResult<T> errorMsg(String msg) {
        return new InfoResult<T>(500, msg, null);
    }

    public static <T> InfoResult<T> errorMsg(int status, String msg) {
        return new InfoResult<T>(status, msg, null);
    }

    public static <T> InfoResult<T> errorMap(T data) {
        return new InfoResult<T>(501, "error", data);
    }

    public static <T> InfoResult<T> errorTokenMsg(String msg) {
        return new InfoResult<T>(502, msg, null);
    }

    public static <T> InfoResult<T> errorException(String msg) {
        return new InfoResult<T>(555, msg, null);
    }

    public static InfoResult formatToPojo(String jsonData, Class<?> clazz) {
        try {
            if (clazz == null) {
                return (InfoResult)MAPPER.readValue(jsonData, InfoResult.class);
            }
            JsonNode jsonNode = MAPPER.readTree(jsonData);
            JsonNode data = jsonNode.get("data");
            Object obj = null;
            if (clazz != null) {
                if (data.isObject()) {
                    obj = MAPPER.readValue(data.traverse(), clazz);
                } else if (data.isTextual()) {
                    obj = MAPPER.readValue(data.asText(), clazz);
                }
            }
            return InfoResult.build(jsonNode.get("status").intValue(), jsonNode.get("msg").asText(), obj);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static InfoResult format(String json) {
        try {
            return (InfoResult)MAPPER.readValue(json, InfoResult.class);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public static InfoResult formatToList(String jsonData, Class<?> clazz) {
        try {
            JsonNode jsonNode = MAPPER.readTree(jsonData);
            JsonNode data = jsonNode.get("data");
            Object obj = null;
            if (data.isArray() && data.size() > 0) {
                obj = MAPPER.readValue(data.traverse(), (JavaType)MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
            }
            return InfoResult.build(jsonNode.get("status").intValue(), jsonNode.get("msg").asText(), obj);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Boolean checkResult(InfoResult result) {
        return result != null && result.getData() != null;
    }

    public Boolean isSUCCESS() {
        return this.status == 200;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String toString() {
        return "InfoResult(status=" + this.getStatus() + ", msg=" + this.getMsg() + ", data=" + this.getData() + ")";
    }
}
