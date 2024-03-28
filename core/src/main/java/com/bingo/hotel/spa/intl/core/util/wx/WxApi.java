package com.bingo.hotel.spa.intl.core.util.wx;


import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.bingo.hotel.spa.intl.core.util.wx.vo.HttpMethod;
import com.bingo.hotel.spa.intl.core.util.wx.vo.Token;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * 微信 API、微信基本接口
 */
@Slf4j
public class WxApi {

    // 小程序获取session ->auth.code2Session
    public static final String MP_GET_SESSION = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";
    public static final String MP_TEMPLATE = "https://api.weixin.qq.com/cgi-bin/message/wxopen/template/send?access_token=%s";
    //获取小程序码
    public static final String MP_GET_CODE = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=%s";
    // token 接口
    public static final String TOKEN = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    // token 接口
    public static final String MP_TOKEN = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    // 发送模板消息
    public static final String SEND_TEMPLATE = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s";
    // 企业微信发送欢迎语
    public static final String WELCOME_TEMPLATE = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/send_welcome_msg?access_token=%s";

    // 公众号 网页授权OAuth2.0获取code
    public static final String GET_OAUTH_CODE = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=%s&scope=%s&state=%s&&connect_redirect=1#wechat_redirect";
    // 公众号 网页授权OAuth2.0获取token
    public static final String GET_OAUTH_TOKEN = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    // 公众号 网页授权OAuth2.0获取用户信息
    public static final String GET_OAUTH_USERINFO = "https://api.weixin.qq.com/cgi-bin/user/info?access_token=%s&openid=%s&lang=zh_CN";

    public static String getSessionUrl(String appId, String appSecret, String jsCode) {
        return String.format(MP_GET_SESSION, appId, appSecret, jsCode);
    }

    public static String getSendMpTemplateUrl(String accessToken) {
        return String.format(MP_TEMPLATE, accessToken);
    }

    // 获取发送模板消息接口
    public static String getSendWelComeTemplateUrl(String accessToken) {
        return String.format(WELCOME_TEMPLATE, accessToken);
    }

    // 获取发送模板消息接口
    public static String getSendTemplateUrl(String accessToken) {
        return String.format(SEND_TEMPLATE, accessToken);
    }

    // 获取token接口
    public static String getTokenUrl(String appId, String appSecret, String type) {
        return String.format(TOKEN, appId, appSecret);
    }

    public static Token getMinPromSession(String appId, String appSecret, String jsCode) {
        try {
            String sessionUrl = getSessionUrl(appId, appSecret, jsCode);
            log.info("get wx session appId={},request={}", appId, sessionUrl);
            JSONObject jsonObject = httpsRequest(sessionUrl, HttpMethod.GET, null);
            log.info("get wx session appId={},response={}", appId, JsonUtils.writeObject2Json(jsonObject));

            String json = JsonUtils.writeObject2Json(jsonObject);
            if (json.contains("errcode")) {
                String errCode = jsonObject.getString("errcode");
                if (!"0".equals(errCode)) {
                    return null;
                }
            }

            Token token = new Token();
            token.setOpenid(jsonObject.getString("openid")); //	string	用户唯一标识
//			token.setUnionId(jsonObject.getString("session_key")); //	string	会话密钥
            token.setSessionKey(jsonObject.getString("session_key")); //	string	用户在开放平台的唯一标识符，在满足 UnionID 下发条件的情况下会返回，详见 UnionID 机制说明。

            if (jsonObject.containsKey("unionid")) {
                token.setUnionId(jsonObject.getString("unionid")); //
            }

            return token;

        } catch (Exception e) {
            log.error("get session error! jsCode=" + jsCode, e);
            return null;
        }
    }

    /**
     * 通用接口
     */
    // 获取接口访问凭证
    public static Token getToken(String appId, String appSecret, String type) {
        Token token = null;
        String tockenUrl = WxApi.getTokenUrl(appId, appSecret, type);
        JSONObject jsonObject = httpsRequest(tockenUrl, HttpMethod.GET, null);
        if (null != jsonObject && jsonObject.containsKey("access_token") && !"".equals(jsonObject.getString("access_token"))) {
            try {
                token = new Token();
                token.setAccessToken(jsonObject.getString("access_token"));
                token.setExpiresIn(jsonObject.getInteger("expires_in"));
            } catch (JSONException e) {
                token = null;// 获取token失败
            }
        } else if (null != jsonObject) {
            token = new Token();
            token.setErrcode(jsonObject.getString("errcode"));
        }
        return token;
    }


    public static byte[] getMPCode(String accessToken, String scene, String page, boolean isHyaline, int with) {
        JSONObject params = new JSONObject();
        params.put("scene", scene);
        params.put("page", page);
        params.put("is_hyaline", isHyaline);
        if (with > 0) {
            params.put("width", with);
        }
        String url = String.format(MP_GET_CODE, accessToken);
        return httpsRequestWithByte(url, params.toString());
    }

    public static byte[] getMPCode(String accessToken, String scene, String page) {
        return getMPCode(accessToken, scene, page, false, 0);
    }

    // 发送请求
    public static JSONObject httpsRequest(String requestUrl, String requestMethod, String outputStr) {
        JSONObject jsonObject = null;

        try {
            TrustManager[] tm = {new JEEWeiXinX509TrustManager()};
            SSLContext sslContext = SSLContext.getInstance("SSL", "SunJSSE");
            sslContext.init(null, tm, new java.security.SecureRandom());
            SSLSocketFactory ssf = sslContext.getSocketFactory();

            URL url = new URL(requestUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ssf);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod(requestMethod);
            if (null != outputStr) {
                OutputStream outputStream = conn.getOutputStream();
                outputStream.write(outputStr.getBytes("UTF-8"));
                outputStream.close();
            }
            InputStream inputStream = conn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String str;
            StringBuilder buffer = new StringBuilder();
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
            conn.disconnect();
            jsonObject = JSONObject.parseObject(buffer.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    // 网页授权OAuth2.0获取code
    public static String getOAuthCodeUrl(String appId, String redirectUrl, String scope, String state) {
        return String.format(GET_OAUTH_CODE, appId, urlEncodeUTF8(redirectUrl), "code", scope, state);
    }

    // 网页授权OAuth2.0获取token
    public static String getOAuthTokenUrl(String appId, String appSecret, String code) {
        return String.format(GET_OAUTH_TOKEN, appId, appSecret, code);
    }

    // 网页授权OAuth2.0获取用户信息
    public static String getOAuthUserInfoUrl(String accessToken, String openid) {
        return String.format(GET_OAUTH_USERINFO, accessToken, openid);
    }

    // 获取OAuth2.0 Token
    public static Token getOAuthToken(String appId, String appSecret, String code) {
        Token token = null;
        String tockenUrl = WxApi.getOAuthTokenUrl(appId, appSecret, code);
        log.info("wxApi.getOAuthToken() request >>>>>> appId={},appSecret={},code={}", appId, appSecret, code);
        JSONObject jsonObject = httpsRequest(tockenUrl, HttpMethod.GET, null);

        log.info("wxApi.getOAuthToken() response >>>>>> appId={}, result={}", JsonUtils.writeObject2Json(jsonObject));

        if (null != jsonObject && !jsonObject.containsKey("errcode")) {
            try {
                token = new Token();
                token.setAccessToken(jsonObject.getString("access_token"));
                token.setExpiresIn(jsonObject.getInteger("expires_in"));
                token.setOpenid(jsonObject.getString("openid"));
                token.setScope(jsonObject.getString("scope"));
            } catch (JSONException e) {
                token = null;// 获取token失败
            }
        } else if (null != jsonObject) {
            token = new Token();
            token.setErrcode(jsonObject.getString("errcode"));
        }
        return token;
    }

    // UTF-8转换
    private static String urlEncodeUTF8(String str) {
        String result = str;
        try {
            result = URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static byte[] httpsRequestWithByte(String requestUrl, String params) {
        HttpsURLConnection conn = null;
        try {
            TrustManager[] tm = {new JEEWeiXinX509TrustManager()};
            SSLContext sslContext = SSLContext.getInstance("SSL", "SunJSSE");
            sslContext.init(null, tm, new java.security.SecureRandom());
            SSLSocketFactory ssf = sslContext.getSocketFactory();

            URL url = new URL(requestUrl);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ssf);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod(HttpMethod.POST);
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(params.getBytes("UTF-8"));
            outputStream.close();
            InputStream inputStream = conn.getInputStream();
            return readInputStream(inputStream);
        } catch (Exception e) {
            log.error("httpsRequestWithByte() error", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        // 创建一个Buffer字符串
        byte[] buffer = new byte[1024];
        // 每次读取的字符串长度，如果为-1，代表全部读取完毕
        int len = 0;
        // 使用一个输入流从buffer里把数据读取出来
        while ((len = inStream.read(buffer)) != -1) {
            // 用输出流往buffer里写入数据，中间参数代表从哪个位置开始读，len代表读取的长度
            outStream.write(buffer, 0, len);
        }
        // 关闭输入流
        inStream.close();
        // 把outStream里的数据写入内存
        return outStream.toByteArray();
    }
}

class JEEWeiXinX509TrustManager implements X509TrustManager {
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    }

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}