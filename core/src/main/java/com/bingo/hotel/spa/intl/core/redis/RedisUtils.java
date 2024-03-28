package com.bingo.hotel.spa.intl.core.redis;

import com.google.common.collect.HashMultimap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@Slf4j
public class RedisUtils {


    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Resource
    private RedisTemplate<String, byte[]> byteRedisTemplate;


    /**
     * 写入缓存（byte[] 类型的值）
     *
     * @param key   redis键
     * @param value redis值
     * @return 是否成功
     */
    public boolean set(final String key, byte[] value) {
        boolean result = false;
        try {
            ValueOperations<String, byte[]> operations = byteRedisTemplate.opsForValue();
            operations.set(key, value);
            result = true;
        } catch (Exception e) {
            log.error("RedisUtils set is error key:{}", key);
        }
        return result;
    }

    /**
     * 写入缓存设置时效时间（byte[] 类型的值）
     *
     * @param key        redis键
     * @param value      redis值
     * @param expireTime 过期时间（秒）
     * @return 是否成功
     */
    public boolean setex(final String key, byte[] value, Long expireTime) {
        boolean result = false;
        try {
            ValueOperations<String, byte[]> operations = byteRedisTemplate.opsForValue();
            operations.set(key, value, Duration.ofSeconds(expireTime));
            result = true;
        } catch (Exception e) {
            log.error("RedisUtils setex is error key:{}", key);
        }
        return result;
    }

    /**
     * 从Redis中读取缓存（byte[] 类型的值）
     *
     * @param key Redis键
     * @return 存储的值，如果不存在则返回null
     */
    public byte[] getBytes(final String key) {
        byte[] result = null;
        try {
            ValueOperations<String, byte[]> operations = byteRedisTemplate.opsForValue();
            result = operations.get(key);
        } catch (Exception e) {
            log.error("RedisUtils getBytes is error key:{}", key);
        }
        return result;
    }


    /**
     * 写入缓存
     *
     * @param key   redis键
     * @param value redis值
     * @return 是否成功
     */
    public boolean set(final String key, String value) {
        boolean result = false;
        try {
            ValueOperations<String, String> operations = redisTemplate.opsForValue();
            operations.set(key, value);
            result = true;
        } catch (Exception e) {
            log.error("RedisUtils is string set error key:{}", key);
        }
        return result;
    }

    /**
     * 写入缓存设置时效时间
     *
     * @param key   redis键
     * @param value redis值
     * @return 是否成功
     */
    public boolean setex(final String key, String value, Long expireTime) {
        boolean result = false;
        try {
            ValueOperations<String, String> operations = redisTemplate.opsForValue();
            operations.set(key, value, Duration.ofSeconds(expireTime));
            result = true;
        } catch (Exception e) {
            log.error("RedisUtils string setex is error key:{}", key);
        }
        return result;
    }

    /**
     * 批量删除对应的键值对
     *
     * @param keys Redis键名数组
     */
    public void removeByKeys(final String... keys) {
        for (String key : keys) {
            remove(key);
        }
    }

    /**
     * 批量删除Redis key
     *
     * @param pattern 键名包含字符串&#xff08;如&#xff1a;myKey*&#xff09;
     */
    public void removePattern(final String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && keys.size() > 0) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 删除key,也删除对应的value
     *
     * @param key Redis键名
     */
    public void remove(final String key) {
        if (exists(key)) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 判断缓存中是否有对应的value
     *
     * @param key Redis键名
     * @return 是否存在
     */
    public Boolean exists(final String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 读取缓存
     *
     * @param key Redis键名
     * @return 是否存在
     */
    public String get(final String key) {
        String result = null;
        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        result = operations.get(key);
        return result;
    }

    /**
     * 哈希 添加
     *
     * @param key     Redis键
     * @param hashKey 哈希键
     * @param value   哈希值
     */
    public void hmSet(String key, String hashKey, String value) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(key, hashKey, value);
    }

    /**
     * 哈希获取数据
     *
     * @param key     Redis键
     * @param hashKey 哈希键
     * @return 哈希值
     */
    public String hmGet(String key, String hashKey) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        return hash.get(key, hashKey);
    }

    /**
     * 判断hash是否存在键
     *
     * @param key     Redis键
     * @param hashKey 哈希键
     * @return 是否存在
     */
    public boolean hmHasKey(String key, String hashKey) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        return hash.hasKey(key, hashKey);
    }

    /**
     * 删除hash中一条或多条数据
     *
     * @param key      Redis键
     * @param hashKeys 哈希键名数组
     * @return 删除数量
     */
    public long hmRemove(String key, String... hashKeys) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        return hash.delete(key, hashKeys);
    }

    /**
     * 获取所有哈希键值对
     *
     * @param key Redis键名
     * @return 哈希Map
     */
    public Map<String, String> hashMapGet(String key) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        return hash.entries(key);
    }

    /**
     * 保存Map到哈希
     *
     * @param key Redis键名
     * @param map 哈希Map
     */
    public void hashMapSet(String key, Map<String, String> map) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.putAll(key, map);
    }

    /**
     * 列表-追加值
     *
     * @param key   Redis键名
     * @param value 列表值
     */
    public void lPush(String key, String value) {
        ListOperations<String, String> list = redisTemplate.opsForList();
        list.leftPush(key, value);
    }

    /**
     * 列表-删除值
     *
     * @param key   Redis键名
     * @param value 列表值
     */
    public void lRemove(String key, String value) {
        ListOperations<String, String> list = redisTemplate.opsForList();
        list.remove(key, 0, value);
    }


    /**
     * 列表-获取指定范围数据
     *
     * @param key   Redis键名
     * @param start 开始行号&#xff08;start:0&#xff0c;end:-1查询所有值&#xff09;
     * @param end   结束行号
     * @return 列表
     */
    public List<String> lRange(String key, long start, long end) {
        ListOperations<String, String> list = redisTemplate.opsForList();
        return list.range(key, start, end);
    }

    /**
     * 集合添加
     *
     * @param key   Redis键名
     * @param value 值
     */
    public void add(String key, String value) {
        SetOperations<String, String> set = redisTemplate.opsForSet();
        set.add(key, value);
    }

    /**
     * 集合获取
     *
     * @param key Redis键名
     * @return 集合
     */
    public Set<String> setMembers(String key) {
        SetOperations<String, String> set = redisTemplate.opsForSet();
        return set.members(key);
    }

    /**
     * 有序集合添加
     *
     * @param key   Redis键名
     * @param value 值
     * @param score 排序号
     */
    public void zAdd(String key, String value, double score) {
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        zSet.add(key, value, score);
    }

    /**
     * 有序集合-获取指定范围
     *
     * @param key        Redis键
     * @param startScore 开始序号
     * @param endScore   结束序号
     * @return 集合
     */
    public Set<String> rangeByScore(String key, double startScore, double endScore) {
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();
        return zset.rangeByScore(key, startScore, endScore);
    }

    /**
     * 计数器
     *
     * @param key Redis键名
     * @return 是否存在
     */
    public Long incr(final String key,Long expireTime) {
        Long result = null;
        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        result = operations.increment(key);
        redisTemplate.expire(key,expireTime,TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * 模糊查询Redis键名
     *
     * @param pattern 键名包含字符串&#xff08;如&#xff1a;myKey*&#xff09;
     * @return 集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 获取多个hashMap
     *
     * @param keySet
     * @return List<Map < String, String>> hashMap列表
     */
    public List hashMapList(Collection<String> keySet) {
        return redisTemplate.executePipelined(new SessionCallback<String>() {
            @Override
            public <K, V> String execute(RedisOperations<K, V> operations) throws DataAccessException {
                HashOperations hashOperations = operations.opsForHash();
                for (String key : keySet) {
                    hashOperations.entries(key);
                }
                return null;
            }
        });
    }

    /**
     * 获取多个hashMap
     *
     * @param keySet Redis中hash结构的key集合
     * @return Map<String, List<Map.Entry<String, String>>> 其中key是redisKey，value是对应的hashMap条目列表
     */
    public Map<String, Map<String, String>> hashMapListAndKey(Collection<String> keySet) {
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<List>() {
            @Override
            public <K, V> List execute(RedisOperations<K, V> operations) throws DataAccessException {
                HashOperations<K, String, String> hashOps = operations.opsForHash();
                for (String key : keySet) {
                    hashOps.entries((K) key); // Cast to K just for example purpose. In real case, K should be of type String.
                }
                return null; // 在管道中，这里返回的值将被忽略
            }
        });

        Iterator<String> keyIter = keySet.iterator();
        Map<String, Map<String, String>> resultMap = new LinkedHashMap<>();

        for (Object result : results) {
            String key = keyIter.next();
            @SuppressWarnings("unchecked")
            Map<String, String> hash = (Map<String, String>) result;
            resultMap.put(key, hash);
        }

        return resultMap;
    }




    /**
     * 保存多个哈希表&#xff08;HashMap&#xff09;(Redis键名可重复)
     *
     * @param batchMap Map<Redis键名,Map<键,值>>
     */
    public void batchHashMapSet(HashMultimap<String, Map<String, String>> batchMap) {
        // 设置5秒超时时间
        redisTemplate.expire("max", 25, TimeUnit.SECONDS);
        redisTemplate.executePipelined(new RedisCallback<List<Map<String, String>>>() {

            @Override
            public List<Map<String, String>> doInRedis(RedisConnection connection) throws DataAccessException {
                Iterator<Map.Entry<String, Map<String, String>>> iterator = batchMap.entries().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Map<String, String>> hash = iterator.next();
                    // 哈希名,即表名
                    byte[] hashName = redisTemplate.getStringSerializer().serialize(hash.getKey());
                    Map<String, String> hashValues = hash.getValue();
                    Iterator<Map.Entry<String, String>> it = hashValues.entrySet().iterator();
                    // 将元素序列化后缓存&#xff0c;即表的多条哈希记录
                    Map<byte[], byte[]> hashes = new HashMap<byte[], byte[]>();
                    while (it.hasNext()) {
                        // hash中一条key-value记录
                        Map.Entry<String, String> entry = it.next();
                        byte[] key = redisTemplate.getStringSerializer().serialize(entry.getKey());
                        byte[] value = redisTemplate.getStringSerializer().serialize(entry.getValue());
                        hashes.put(key, value);
                    }
                    // 批量保存
                    connection.hMSet(hashName, hashes);
                }
                return null;
            }
        });
    }

    /**
     * 保存多个哈希表&#xff08;HashMap&#xff09;(Redis键名不可以重复)
     *
     * @param dataMap Map<Redis键名,Map<哈希键,哈希值>>
     */
    public void batchHashMapSet(Map<String, Map<String, String>> dataMap) {
        // 设置5秒超时时间
        redisTemplate.expire("max", 25, TimeUnit.SECONDS);
        redisTemplate.executePipelined(new RedisCallback<List<Map<String, String>>>() {

            @Override
            public List<Map<String, String>> doInRedis(RedisConnection connection) throws DataAccessException {
                Iterator<Map.Entry<String, Map<String, String>>> iterator = dataMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Map<String, String>> hash = iterator.next();
                    // 哈希名,即表名
                    byte[] hashName = redisTemplate.getStringSerializer().serialize(hash.getKey());
                    Map<String, String> hashValues = hash.getValue();
                    Iterator<Map.Entry<String, String>> it = hashValues.entrySet().iterator();
                    // 将元素序列化后缓存&#xff0c;即表的多条哈希记录
                    Map<byte[], byte[]> hashes = new HashMap<byte[], byte[]>();
                    while (it.hasNext()) {
                        // hash中一条key-value记录
                        Map.Entry<String, String> entry = it.next();
                        byte[] key = redisTemplate.getStringSerializer().serialize(entry.getKey());
                        byte[] value = redisTemplate.getStringSerializer().serialize(entry.getValue());
                        hashes.put(key, value);
                    }
                    // 批量保存
                    connection.hMSet(hashName, hashes);
                }
                return null;
            }
        });
    }

    /**
     * 保存多个哈希表&#xff08;HashMap&#xff09;列表&#xff08;哈希map的Redis键名不能重复&#xff09;
     *
     * @param list Map<Redis键名,Map<哈希键,哈希值>>
     * @see RedisUtils*.batchHashMapSet()*
     */
    public void batchHashMapListSet(List<Map<String, Map<String, String>>> list) {
        // 设置5秒超时时间
        redisTemplate.expire("max", 25, TimeUnit.SECONDS);
        redisTemplate.executePipelined(new RedisCallback<List<Map<String, String>>>() {

            @Override
            public List<Map<String, String>> doInRedis(RedisConnection connection) throws DataAccessException {
                for (Map<String, Map<String, String>> dataMap : list) {
                    Iterator<Map.Entry<String, Map<String, String>>> iterator = dataMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Map<String, String>> hash = iterator.next();
                        // 哈希名,即表名
                        byte[] hashName = redisTemplate.getStringSerializer().serialize(hash.getKey());
                        Map<String, String> hashValues = hash.getValue();
                        Iterator<Map.Entry<String, String>> it = hashValues.entrySet().iterator();
                        // 将元素序列化后缓存&#xff0c;即表的多条哈希记录
                        Map<byte[], byte[]> hashes = new HashMap<byte[], byte[]>();
                        while (it.hasNext()) {
                            // hash中一条key-value记录
                            Map.Entry<String, String> entry = it.next();
                            byte[] key = redisTemplate.getStringSerializer().serialize(entry.getKey());
                            byte[] value = redisTemplate.getStringSerializer().serialize(entry.getValue());
                            hashes.put(key, value);
                        }
                        // 批量保存
                        connection.hMSet(hashName, hashes);
                    }
                }
                return null;
            }
        });
    }

    /**
     * 获取key存入时间
     * @param key
     * @return
     */
    public Long getKeyDuration(String key) {
        // 检查键是否存在
        Boolean hasKey = redisTemplate.hasKey(key);
        if (hasKey != null && hasKey) {
            // 查询并返回键的剩余生存时间，单位为秒
            return redisTemplate.getExpire(key);
        } else {
            // 如果键不存在，则返回null
            return null;
        }
    }

    /**
     * 保存多个哈希表，并为每个键设置超时时间。
     *
     * @param dataMap      Map<Redis键名, Map<哈希键, 哈希值>>
     * @param timeout      超时时间
     * @param timeUnit     超时时间的单位
     */
    public void batchHashMapSetWithExpire(Map<String, Map<String, String>> dataMap, long timeout, TimeUnit timeUnit) {
        if (dataMap == null || dataMap.isEmpty()) {
            return;
        }

        long timeoutInSeconds = timeUnit.toSeconds(timeout);
        redisTemplate.executePipelined((RedisConnection connection) -> {
            dataMap.forEach((hashKey, hashValues) -> {
                byte[] rawHashKey = redisTemplate.getStringSerializer().serialize(hashKey);
                Map<byte[], byte[]> rawHashValues = new HashMap<>();

                hashValues.forEach((key, value) -> {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize(key);
                    byte[] rawValue = redisTemplate.getStringSerializer().serialize(value);
                    rawHashValues.put(rawKey, rawValue);
                });

                // 批量保存哈希表数据
                connection.hMSet(rawHashKey, rawHashValues);
                // 为哈希表设置超时时间，无论数据是否更改，都更新超时时间
                if (timeout > 0) {
                    connection.expire(rawHashKey, timeoutInSeconds);
                }
            });
            return null;
        });
    }


}
