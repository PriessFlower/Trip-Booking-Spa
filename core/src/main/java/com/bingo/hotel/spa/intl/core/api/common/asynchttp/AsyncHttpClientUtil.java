package com.bingo.hotel.spa.intl.core.api.common.asynchttp;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Slf4j
public class AsyncHttpClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(AsyncHttpClientUtil.class);
    //从池中获取链接超时时间(ms)
    private static final int CONNECTION_REQUEST_TIMEOUT = 10000;
    //建立链接超时时间(ms)
    private static final int CONNECT_TIMEOUT = 10000;
    //读取超时时间(ms)
    private static final int SOCKET_TIMEOUT = 5000;
    //连接数
    private static final int MAX_TOTAL = 50;
    private static final int MAX_PER_ROUTE = 50;

    private static final CloseableHttpAsyncClient httpclient;
    private static PoolingNHttpClientConnectionManager poolManager;

    static {
        httpclient = init();
        httpclient.start();
    }

    private static CloseableHttpAsyncClient init() {
        CloseableHttpAsyncClient client = null;
        try {
            //配置io线程
            IOReactorConfig ioReactorConfig = IOReactorConfig.custom().
                    setIoThreadCount(Runtime.getRuntime().availableProcessors())
                    .setSoKeepAlive(true)
                    .build();
            //创建一个ioReactor
            ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
//            poolManager=new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
            poolManager = new PoolingNHttpClientConnectionManager(ioReactor);
            //设置连接池大小
            poolManager.setMaxTotal(MAX_TOTAL);
            poolManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
            // 配置请求的超时设置
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .build();

            client = HttpAsyncClients.custom()
                    .setConnectionManager(poolManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
            return client;
        } catch (IOReactorException e) {
            log.error("AsyncHttpClientUtil init is error e:{}", e.toString());
        }
        return client;
    }

    public static String get(String url, List<NameValuePair> ns) {
        HttpGet httpget;
        URIBuilder uri = new URIBuilder();
        try {
            if (ns != null) {
                uri.setPath(url);
                uri.addParameters(ns);
                httpget = new HttpGet(uri.build());
            } else {
                httpget = new HttpGet(url);
            }

            // One most likely would want to use a callback for operation result
            httpclient.execute(httpget, new FutureCallback<HttpResponse>() {

                public void completed(final HttpResponse response) {
                    logger.info(httpget.getRequestLine() + "->" + response.getStatusLine());
                    try {
                        logger.info("当前请求状态：" + poolManager.getTotalStats() + ", response="
                                + EntityUtils.toString(response.getEntity()));
                    } catch (IOException e) {
                        log.error("AsyncHttpClientUtil get is error e:{}", e.toString());
                    }
                }

                public void failed(final Exception ex) {
                    logger.info(httpget.getRequestLine() + "->" + ex);
                }

                public void cancelled() {
                    logger.info(httpget.getRequestLine() + " cancelled");
                }

            });

        } catch (Exception e) {
            logger.error("[发送get请求失败]URL:{},异常:", uri.getUserInfo(), e);
        }
        return null;
    }
}
