package com.bingo.hotel.spa.intl.core.api.expedia.utils;

import javax.servlet.http.HttpSession;
import java.net.InetAddress;

/**
 * 网络会话工具类.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/20
 * @since : 1.0
 **/
public class NetWorkUtils {

    public static String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return "127.0.0.1"; // 默认本机IP
        }
    }

    public static HttpSession getCurrentSession(boolean createIfNotExists) {
        try {
            // 假设在一个Servlet环境中
            // 从当前线程关联的请求对象中获取会话
//            return request.getSession(createIfNotExists);
        } catch (Exception e) {
            e.printStackTrace();
            return null; // 无法获取会话时返回null
        }
        return null;
    }
}
