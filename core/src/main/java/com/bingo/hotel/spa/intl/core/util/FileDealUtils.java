package com.bingo.hotel.spa.intl.core.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.zip.GZIPInputStream;

/**
 * 文件处理工具类.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/10
 * @since : 1.0
 **/
public class FileDealUtils {

    /**
     * 通过url下载文件
     *
     * @param remoteFilePath 文件下载地址
     * @param localFilePath  本地存储位置
     */
    public static void downloadFile(String remoteFilePath, String localFilePath) {
        URL website = null;
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            website = new URL(remoteFilePath);
            rbc = Channels.newChannel(website.openStream());
            fos = new FileOutputStream(localFilePath);//本地要存储的文件地址 例如：test.txt
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (rbc != null) {
                try {
                    rbc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * 解压gz后缀文件
     *
     * @param gzFilePath    压缩文件位置
     * @param localFilePath 本地存储位置
     */
    public static void gzipFile(String gzFilePath, String localFilePath) {
        try (InputStream in = new FileInputStream(gzFilePath);
             OutputStream out = new FileOutputStream(localFilePath);
             GZIPInputStream gzipIn = new GZIPInputStream(in)) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
