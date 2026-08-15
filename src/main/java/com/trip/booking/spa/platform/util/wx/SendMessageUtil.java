package com.trip.booking.spa.platform.util.wx;

import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.platform.util.wx.vo.Articles;
import com.trip.booking.spa.platform.util.wx.vo.HttpMethod;
import com.trip.booking.spa.platform.util.wx.vo.News;
import com.trip.booking.spa.platform.util.wx.vo.NewsMessage;
import com.trip.booking.spa.platform.util.wx.vo.Token;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SendMessageUtil {
    public static boolean sendWeChatNewsMsg(String title, String touser, String content, String url) throws Exception {
        try {
            Token token = WxApi.getToken("ww34dd4a11ac5ec3ea", "6w6i7XctR44zV199GWfTutw-qp79XhldeeufqA3qpJc", "");
            String accessToken = token.getAccessToken();
            String sendUrl = WxApi.getSendTemplateUrl(accessToken);
            String sendTxt = buildSendNewsMsg("1000002", title, touser, content, url);
            log.info("微信通知开始发送，发送订单号为：[orderCode=" + "]发送用户为：[" + touser + "],发送地址为：[sendUrl=" + sendUrl + "],发送内容为：[sendTxt=" + sendTxt + "]");
            JSONObject jsonObj = WxApi.httpsRequest(sendUrl, HttpMethod.POST, sendTxt);
            log.info("微信通知发送成功：返回结果：[jsonObj=" + jsonObj + "]");
            if (jsonObj != null) {
                return "0".equals(jsonObj.getString("errcode"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            log.error("调用微信通知接口失败", e);
            throw e;
        }
        return false;
    }

    public static String buildSendNewsMsg(String agentId, String title, String touser, String content, String url) {
        NewsMessage msg = new NewsMessage();
        msg.setMsgtype("news");
        msg.setSafe("0");
        msg.setAgentid(agentId);
        msg.setTouser(touser);
        Articles cles = new Articles();
        cles.setDescription(content);

        cles.setTitle(title);
        cles.setUrl(url);
        List<Articles> als = new ArrayList<>();
        als.add(cles);
        News news = new News();
        news.setArticles(als);
        msg.setNews(news);

        return JsonUtils.writeObject2Json(msg);

    }
}
