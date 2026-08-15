package com.trip.booking.spa.platform.util;

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.tea.TeaException;
import com.trip.booking.spa.platform.util.wx.SendMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DingTalkUtils {
    private static final Logger log = LoggerFactory.getLogger(DingTalkUtils.class);

    /**
     * 使用 Token 初始化账号Client
     *
     * @return Client
     * @throws Exception
     */
    public static com.aliyun.dingtalkoauth2_1_0.Client createClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkoauth2_1_0.Client(config);
    }

    public static GetAccessTokenResponse getToken() throws Exception {
        com.aliyun.dingtalkoauth2_1_0.Client client = createClient();
        com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest getAccessTokenRequest = new com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest()
                .setAppKey("dingpb9wszabvtpjw2z9")
                .setAppSecret("_6Vk0q3-SwjusBKQgZFcvMEOZCkannGV8LPaiZ2CJo5Z4FTDZaBH6kJHpTsQDVj3");
        try {
            return client.getAccessToken(getAccessTokenRequest);
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                err.printStackTrace();
            }
        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                err.printStackTrace();
            }
        }
        return null;
    }

    public static void sendMessage(String orderNo, String qrCodeUrl, String userList, String wxUserList) {
        try {
            SendMessageUtil.sendWeChatNewsMsg("去哪儿订单通知", wxUserList, orderNo, qrCodeUrl);
        } catch (Exception e) {
            log.error("SendMessageError.", e);
        }
    }

    public static com.aliyun.dingtalkrobot_1_0.Client createClientNew() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkrobot_1_0.Client(config);
    }

    public static void robotSendMessage(String orderNo, String message) {
        try {
            com.aliyun.dingtalkrobot_1_0.Client client = createClientNew();
            com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders orgGroupSendHeaders = new com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders();
            orgGroupSendHeaders.xAcsDingtalkAccessToken = getToken().getBody().accessToken;
            String sendMessage = message + "\n" + orderNo;
            com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest orgGroupSendRequest = new com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest()
                    .setMsgParam("{       \"content\": \"" + sendMessage + "\"   }")
                    .setMsgKey("sampleText")
                    .setRobotCode("dingpb9wszabvtpjw2z9")
                    .setOpenConversationId("cidwapxT87HOdzTN1oDi+1BIQ==");

            client.orgGroupSendWithOptions(orgGroupSendRequest, orgGroupSendHeaders, new com.aliyun.teautil.models.RuntimeOptions());
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                log.error("Error robot sending code:{}, message{}", err.code, err.message);
            }

        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                log.error("Error robot sending code:{}, message{}", err.code, err.message);
            }
        }
    }
}
