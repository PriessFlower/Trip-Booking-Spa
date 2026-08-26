package com.trip.booking.spa.platform.http;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.util.Strings;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * 通道层的 HTTP 传输实现：连接池、超时、重试处理器、请求发送与响应读取。
 *
 * <p><b>本类属 ④ 通道层</b>（见 {@code docs/architecture.md} §2），与 {@link BaseHttpAccess}、
 * {@link ChunkedFileAccess} 同层。此前它落在 {@code core/util/}——那是放日期格式化、字符串
 * 处理这类零碎工具的地方，而本类是八家供应商全部对外请求的底层实现，改错即八家同挂。
 * 放在「杂物筐」里既掩盖了它的分量，也使通道层的纪律（统一限流、重试、埋点）管不到它。
 *
 * <p><b>本类不得包含任何供应商语义</b>：不认识任何一家的字段名、错误码或业务含义，
 * 只负责「发得出去、收得回来」。需要解释响应含义时，交由 ③ 适配层。
 */
@Slf4j
public class HttpUtils {

    private static final int TIME_OUT = 10 * 1000;

    /**
     * 每个目标 host 一个连接池，物理隔离（2026-08-26）。
     *
     * <p>此前是全局单例：按<b>第一个到达的 url</b> 的 host 建池，此后所有供应商共用
     * {@code maxTotal=250}——任何一家慢下来（socket 挂满 10s）就吃光全局连接，其余家
     * 一起被拖死；且当时取 port 默认 80，对 https 是错的，{@code setMaxPerRoute} 打在
     * 不存在的路由上，per-route 上限实为 no-op。按 host 分池后一家慢只占满自己的池。
     *
     * <p>键含端口（url authority 原文），同 host 异端口视作两池——它们本就是两个服务。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, CloseableHttpClient> CLIENTS_BY_HOST =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 与 {@link #CLIENTS_BY_HOST} 同键的连接池管理器，留作水位读取（{@link #poolStats()}）用 */
    private static final java.util.concurrent.ConcurrentHashMap<String, PoolingHttpClientConnectionManager>
            MANAGERS_BY_HOST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 单池容量。各家实际并发受限流闸约束（QPS 都在个位/十位数），64 远高于真实在飞数；
     * 单 host 单路由，故 total 与 per-route 同值。
     */
    private static final int MAX_PER_HOST = 64;

    private static void config(HttpRequestBase httpRequestBase) {
//         设置Header等
        httpRequestBase.setHeader("User-Agent", "Mozilla/5.0");
        httpRequestBase.setHeader("Accept", "application/json, text/plain, */*");
        httpRequestBase.setHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        httpRequestBase.setHeader("Accept-Charset", "UTF-8");

        // 配置请求的超时设置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(TIME_OUT)
                .setConnectTimeout(TIME_OUT)
                .setSocketTimeout(TIME_OUT).build();

        httpRequestBase.setConfig(requestConfig);
    }

    private static void configGet(HttpRequestBase httpRequestBase) {
//         设置Header等
        httpRequestBase.setHeader("User-Agent", "Mozilla/5.0");
        httpRequestBase.setHeader("Accept", "application/json, text/plain, */*");
        httpRequestBase.setHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        httpRequestBase.setHeader("Accept-Charset", "UTF-8");

        // 配置请求的超时设置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(TIME_OUT)
                .setConnectTimeout(TIME_OUT)
                .setSocketTimeout(TIME_OUT).build();

        httpRequestBase.setConfig(requestConfig);
    }

    /** 取该 url 目标 host 专属的 HttpClient，见 {@link #CLIENTS_BY_HOST} */
    public static CloseableHttpClient getHttpClient(String url) {
        String authority = url.split("/")[2];
        return CLIENTS_BY_HOST.computeIfAbsent(authority,
                host -> createHttpClient(host, MAX_PER_HOST, MAX_PER_HOST));
    }

    /**
     * 各 host 连接池水位：host → [已借出, 等待者, 空闲, 上限]。
     * 形状同 {@code ThreadPools.stats()}（形式统一，PROJECT.md §4.3），监控接指标只挂这一处。
     */
    public static Map<String, int[]> poolStats() {
        Map<String, int[]> snapshot = new java.util.LinkedHashMap<>();
        MANAGERS_BY_HOST.forEach((host, cm) -> {
            org.apache.http.pool.PoolStats s = cm.getTotalStats();
            snapshot.put(host, new int[]{s.getLeased(), s.getPending(), s.getAvailable(), s.getMax()});
        });
        return snapshot;
    }


    public static <T extends BaseResponse> ResponseResult access(String url, Map<String, String> headers,
                                                                 String params, IParser<T> parser)
            throws Exception {
        HttpClient httpClient = getHttpClient(url);

        HttpPost httpPost = new HttpPost(url);
        config(httpPost);
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpPost.addHeader(e.getKey(), e.getValue());
            }
        }

        if (StringUtils.isNotBlank(params)) {
            StringEntity entity = new StringEntity(params, ContentType.APPLICATION_JSON);

            httpPost.setEntity(entity);
        }
        long start = System.currentTimeMillis();
        HttpResponse response = httpClient.execute(httpPost);
//        log.info("access>>>request:{},response:{}", JsonUtils.writeObject2Json(httpPost), JsonUtils.writeObject2Json(response));
        HttpEntity entity = response.getEntity();

        String entityStr;
        T data;
        long start1 = System.currentTimeMillis();

        if (entity == null && response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            entityStr = Strings.EMPTY;
            data = parser.parseError(entityStr);
        } else {
            entityStr = EntityUtils.toString(entity, "UTF-8");
            data = parser.parse(entityStr);
        }
        ResponseResult<T> result = new ResponseResult(response.getStatusLine().getStatusCode(), entityStr, data);

        EntityUtils.consume(entity);
        return result;
    }

    public static <T extends BaseResponse> ResponseResult accessGet(String url, Map<String, String> headers,
                                                                    Map<String, String> params, IParser<T> parser)
            throws Exception {
        HttpClient httpClient = getHttpClient(url);

        HttpGet httpGet = new HttpGet(buildUrl(url, params));
        configGet(httpGet);
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpGet.addHeader(e.getKey(), e.getValue());
            }
        }

        HttpResponse response = httpClient.execute(httpGet);
//        log.info("accessGet>>>request:{},response:{}", JsonUtils.writeObject2Json(httpGet), JsonUtils.writeObject2Json(response));
        HttpEntity entity = response.getEntity();

        String entityStr;
        T data;
        if (entity == null && response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            entityStr = Strings.EMPTY;
            data = parser.parseError(entityStr);
        } else {
            entityStr = EntityUtils.toString(entity, "UTF-8");
            data = parser.parse(entityStr);
        }

        ResponseResult<T> result = new ResponseResult(response.getStatusLine().getStatusCode(), entityStr, data);
        EntityUtils.consume(entity);
        return result;
    }

    /**
     * DELETE，形状与 {@link #accessGet} 一致，复用同一套超时配置。
     *
     * <p>调用方通常直接传入供应商响应里给出的完整链接（含 token），故不再拼接参数。
     *
     * <p><b>与 GET 的关键差异</b>：DELETE 成功常返回 204 且无响应体，故此处不能像 GET 那样
     * 以「无 entity」判失败——无 entity 恰恰是成功的常态。是否成功一律以状态码为准。
     */
    public static <T extends BaseResponse> ResponseResult accessDelete(String url, Map<String, String> headers,
                                                                       IParser<T> parser) throws Exception {
        HttpClient httpClient = getHttpClient(url);

        HttpDelete httpDelete = new HttpDelete(url);
        configGet(httpDelete);
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpDelete.addHeader(e.getKey(), e.getValue());
            }
        }

        HttpResponse response = httpClient.execute(httpDelete);
        int status = response.getStatusLine().getStatusCode();
        HttpEntity entity = response.getEntity();
        String entityStr = entity == null ? Strings.EMPTY : EntityUtils.toString(entity, "UTF-8");

        T data = status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES
                ? parser.parse(entityStr)
                : parser.parseError(entityStr);

        ResponseResult<T> result = new ResponseResult(status, entityStr, data);
        if (entity != null) {
            EntityUtils.consume(entity);
        }
        return result;
    }

    /**
     * get
     *
     * @param url
     * @param headers
     * @param params
     * @return
     * @throws Exception
     */
    public static String doGet(String url, Map<String, String> headers, Map<String, String> params)
            throws Exception {
        HttpClient httpClient = getHttpClient(url);

        HttpGet httpGet = new HttpGet(buildUrl(url, params));
        configGet(httpGet);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            httpGet.addHeader(e.getKey(), e.getValue());
        }
        long start = System.currentTimeMillis();
        HttpResponse response = httpClient.execute(httpGet);
//        log.info("doGet>>>request:{},response:{}", JsonUtils.writeObject2Json(httpGet), JsonUtils.writeObject2Json(response));
        HttpEntity entity = response.getEntity();
        if (entity == null && response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            return "";
        }
        String result = EntityUtils.toString(entity, "UTF-8");
        EntityUtils.consume(entity);

        return result;
    }

    /**
     * post form
     *
     * @param url
     * @param headers
     * @param params
     * @return
     * @throws Exception
     */
    public static String doPost(String url, Map<String, String> headers, Map<String, String> params)
            throws Exception {
        HttpClient httpClient = getHttpClient(url);

        HttpPost httpPost = new HttpPost(url);
        config(httpPost);
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpPost.addHeader(e.getKey(), e.getValue());
            }
        }

        if (MapUtils.isNotEmpty(params)) {
            List<NameValuePair> nameValuePairList = Lists.newArrayList();

            for (String key : params.keySet()) {
                nameValuePairList.add(new BasicNameValuePair(key, params.get(key)));
            }
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(nameValuePairList, "utf-8");
            formEntity.setContentType("application/x-www-form-urlencoded; charset=UTF-8");
            httpPost.setEntity(formEntity);
        }

        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (entity == null && response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            return "";
        }
        String result = EntityUtils.toString(entity, "UTF-8");
        EntityUtils.consume(entity);
        return result;
    }


    /**
     * 处理post请求
     *
     * @param url     地址
     * @param params  参数
     * @param headers 请求头 header key-value
     * @return
     */
    public static String doPostObject(String url, Map<String, Object> params, Map<String, Object> headers) {
        HttpResponse response = null;
        try {
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            //1.处理参数
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            String jsonParam = JSON.toJSONString(params);
            StringEntity se = new StringEntity(jsonParam, ContentType.APPLICATION_JSON);
//            se.setContentType("application/json");
            post.setEntity(se);
            //2.处理请求头信息
            if (headers != null && !headers.isEmpty()) {
                for (String key : headers.keySet()) {
                    post.setHeader(key, String.valueOf(headers.get(key)));
                }
            }
//            log.info("HttpClientUtil-doPost>>>requestBody:{}", JSON.toJSONString(params));
            //3.请求数据
            response = httpClient.execute(post);
//            log.info("HttpClientUtil-doPost>>>request:{},response:{}", JSON.toJSONString(post), JSON.toJSONString(response));
            //4.解析数据
            String result = null;
            if (response != null) {
                result = handleData(response.getEntity().getContent());
            }
//            log.info("HttpClientUtil-doPost>>>responseBody:{}", JSON.toJSONString(result));
            return result;
        } catch (Exception e) {
            log.info("HttpClientUtil-POST 出错：{}", e.getMessage());
            return null;
        }
    }


    /**
     * 流对象
     *
     * @param is 流
     * @return
     * @throws Exception
     */
    private static String handleData(InputStream is) throws Exception {
        int len = 0;
        //将is字节流，转化为字符流
        InputStreamReader reader = new InputStreamReader(is);
        //创建StringBuffer对象
        char[] buf = new char[1024];
        StringBuffer result = new StringBuffer();
        while ((len = reader.read(buf)) != -1) {
            result.append(String.valueOf(buf, 0, len));
        }
        reader.close();
        return String.valueOf(result);
    }

    private static String buildUrl(String url, Map<String, String> params)
            throws UnsupportedEncodingException {
        StringBuilder sbUrl = new StringBuilder();
        sbUrl.append(url);

        if (MapUtils.isNotEmpty(params)) {
            StringBuilder sbQuery = new StringBuilder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (0 < sbQuery.length()) {
                    sbQuery.append("&");
                }
                if (StringUtils.isBlank(param.getKey()) && !StringUtils.isBlank(param.getValue())) {
                    sbQuery.append(param.getValue());
                }
                if (!StringUtils.isBlank(param.getKey())) {
                    sbQuery.append(param.getKey());
                    if (!StringUtils.isBlank(param.getValue())) {
                        sbQuery.append("=");
                        sbQuery.append(URLEncoder.encode(param.getValue(), "utf-8"));
                    }
                }
            }
            if (0 < sbQuery.length()) {
                sbUrl.append("?").append(sbQuery);
            }
        }

        return sbUrl.toString();
    }

    private static CloseableHttpClient createHttpClient(String host, int maxTotal, int maxPerRoute) {
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry =
                RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf)
                        .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        cm.setMaxTotal(maxTotal);
        // 单 host 客户端只有一条路由；此前那个按 (hostname, port=80) 定制路由的写法对
        // https 打在不存在的路由上，是 no-op，随分池一并删除
        cm.setDefaultMaxPerRoute(maxPerRoute);
        MANAGERS_BY_HOST.put(host, cm);
        //请求重试处理
        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= 5) {
                    //如果已经重试了5次，就放弃
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {
                    //如果服务器丢掉了连接，那么就重试
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {
                    //不要重试SSL握手异常
                    return false;
                }
                if (exception instanceof InterruptedIOException) {
                    // 超时
                    return false;
                }
                if (exception instanceof UnknownHostException) {
                    //目标服务器不可达
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {
                    //连接被拒绝
                    return false;
                }
                if (exception instanceof SSLException) {
                    // SSL握手异常
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                //如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };
        CloseableHttpClient httpClient =
                HttpClients.custom().setConnectionManager(cm).setRetryHandler(httpRequestRetryHandler).build();
        return httpClient;
    }

}
