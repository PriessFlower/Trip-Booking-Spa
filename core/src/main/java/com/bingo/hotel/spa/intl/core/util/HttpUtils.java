package com.bingo.hotel.spa.intl.core.util;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.util.Strings;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

@Slf4j
public class HttpUtils {

    private static final int TIME_OUT = 100 * 1000;
    private static CloseableHttpClient HTTP_CLIENT = null;
    private final static Object SYNC_LOCK = new Object();

    private static void config(HttpRequestBase httpRequestBase) {
//         设置Header等
        httpRequestBase.setHeader("User-Agent", "Mozilla/5.0");
        httpRequestBase.setHeader("Accept", "application/json, text/plain, */*");
        httpRequestBase.setHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        httpRequestBase.setHeader("Accept-Charset", "UTF-8");
        httpRequestBase.setHeader("Content-Type", "application/json");

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

    /**
     * 获取HttpClient对象
     *
     * @param url
     * @return
     */
    public static CloseableHttpClient getHttpClient(String url) {
        String hostname = url.split("/")[2];
        int port = 80;
        if (hostname.contains(":")) {
            String[] arr = hostname.split(":");
            hostname = arr[0];
            port = Integer.parseInt(arr[1]);
        }
        if (HTTP_CLIENT == null) {
            synchronized (SYNC_LOCK) {
                if (HTTP_CLIENT == null) {
                    HTTP_CLIENT = createHttpClient(200, 40, 100, hostname, port);
                }
            }
        }
        return HTTP_CLIENT;
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
        log.info("http调用耗时:{}", System.currentTimeMillis() - start);
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
        log.info("http结果转义调用耗时:{}", System.currentTimeMillis() - start1);
        ResponseResult<T> result = new ResponseResult(response.getStatusLine().getStatusCode(), entityStr, data);

        EntityUtils.consume(entity);
        return result;
    }

    public static void main(String[] args) {

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
        HttpResponse response = httpClient.execute(httpGet);
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

    public static CloseableHttpClient createHttpClient(int maxTotal, int maxPerRoute, int maxRoute, String hostname,
                                                       int port) {
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry =
                RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf)
                        .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        //将最大连接数增加
        cm.setMaxTotal(maxTotal);
        // 将每个路由基础的连接增加
        cm.setDefaultMaxPerRoute(maxPerRoute);
        HttpHost httpHost = new HttpHost(hostname, port);
        //将目标主机的最大连接数增加
        cm.setMaxPerRoute(new HttpRoute(httpHost), maxRoute);
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
