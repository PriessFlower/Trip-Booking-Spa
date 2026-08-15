package com.trip.booking.spa.platform.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * json处理工具类
 *
 * @author weiliang.chen
 */

@Slf4j
public class JsonUtils {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        OBJECT_MAPPER.configure(JsonParser.Feature.IGNORE_UNDEFINED, true);
        OBJECT_MAPPER.registerModule(new Jdk8Module());
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * 将json转化为Map
     *
     * @param json 要解析的json
     * @return map
     */
    public static Map readJson2Map(String json) {
        Map maps = null;
        try {
            maps = OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("fail to readJson2Map.Json : {}", json);
        }
        return maps;
    }

    /**
     * 将json转化为Map
     *
     * @param json 要解析的json
     * @return map
     */
    public static Map<String, String> readJson2MapStr(String json) {
        Map<String, String> maps = null;
        try {
            maps = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.error("fail to readJson2MapStr.Json : {}", json);
        }
        return maps;
    }

    /**
     * 转化Object为Json
     *
     * @param object 要转的对象
     * @return json
     */
    public static String writeObject2Json(Object object) {
        StringWriter json = new StringWriter();
        try {
            OBJECT_MAPPER.writeValue(json, object);
        } catch (Exception e) {
            log.error("fail to writeObject2Json. object : {}", object.toString(), e);
        }

        return json.toString();
    }

    public static String getStringByKey(JsonNode jNode, String key) {
        JsonNode jNodeVal = jNode.get(key);
        if (jNodeVal != null) {
            return jNodeVal.asText();
        }
        return null;
    }

    /**
     * JSON串转换为Java泛型对象，可以是各种类型，此方法最为强大。用法看测试用例。
     *
     * @param <T>
     * @param jsonString JSON字符串
     * @param tr         TypeReference,例如: new TypeReference< List<FamousUser> >(){}
     * @return List对象列表
     */
    public static <T> T decodeJson(String jsonString, TypeReference<T> tr) {
        if (StringUtils.isEmpty(jsonString)) {
            return null;
        }

        try {
            return (T) OBJECT_MAPPER.readValue(jsonString, tr);
        } catch (Exception e) {
            log.warn("json error:" + e.getMessage());
        }
        return null;
    }

    //返回有序后的string
    public static String convertNode(final JsonNode node) {
        try {
            final Object obj = OBJECT_MAPPER.treeToValue(node, Object.class);
            final String json = OBJECT_MAPPER.writeValueAsString(obj);
            return json;
        } catch (Exception e) {
            log.warn("json convertNode error:" + e.getMessage());
            return "";
        }
    }

    public static <T> T readValue(String jsonStr, Class<T> clazz) {
        return readValue(jsonStr, clazz, StringUtils.EMPTY);
    }

    public static <T> T readValue(String jsonStr, Class<T> clazz, String logInfo) {
        if (StringUtils.isEmpty(jsonStr)) {
            return null;
        }
        T t = null;
        try {
            t = OBJECT_MAPPER.readValue(jsonStr, clazz);
        } catch (IOException e) {
            log.warn("JsonUtils readValue IOException className:{} info:{} message:{}", clazz.getSimpleName(), logInfo,
                    e.getMessage(), e);
        }
        return t;
    }

    public static JsonNode readTree(String jsonStr) {
        JsonNode result = null;
        try {
            result = OBJECT_MAPPER.readTree(jsonStr);
        } catch (IOException e) {
            log.warn("JsonUtils readTree IOException json={}", jsonStr, e);
            throw new RuntimeException("parse json error: " + jsonStr);
        }
        return result;
    }

    public static String getChildText(String json, String childTag) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        JsonNode rootNode = readTree(json);
        if (rootNode == null) {
            return null;
        }
        JsonNode node = rootNode.get(childTag);
        if (node == null) {
            return null;
        }
        String data = node.asText();
        return data;
    }

    public static String getChildText(JsonNode rootNode, String childTag) {
        JsonNode node = rootNode.get(childTag);
        if (node == null || node.isNull()) {
            return null;
        }
        String data = node.asText();
        return data;
    }

}
