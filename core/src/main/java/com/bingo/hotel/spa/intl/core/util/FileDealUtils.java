package com.bingo.hotel.spa.intl.core.util;

import com.github.luben.zstd.ZstdInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * 文件处理工具类.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/10
 * @since : 1.0
 **/

@Slf4j
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
            // 设置代理服务器信息
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 33210));
            URLConnection urlConnection = website.openConnection(proxy);
            rbc = Channels.newChannel(urlConnection.getInputStream());
//            rbc = Channels.newChannel(website.openStream());
            fos = new FileOutputStream(localFilePath);//本地要存储的文件地址 例如：a/b/test.txt
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

    /**
     * 解压zst后缀文件并分块存储
     *
     * @param zstdFilePath  压缩文件位置
     * @param localFilePath 本地存储位置
     */

//    public static void zstdFiles(String zstdFilePath, String localFilePath) {
//        int chunkSize = 1024 * 1024 * 1024; // 分片大小，默认1GB
//
//        try (InputStream inputStream = new FileInputStream(zstdFilePath);
//             ZstdInputStream zstdInputStream = new ZstdInputStream(inputStream)) {
//            byte[] buffer = new byte[chunkSize];
//            int bytesRead;
//            int chunkCount = 1;
//            while ((bytesRead = zstdInputStream.read(buffer)) != -1) {
//                String outputFilePath = localFilePath + "_" + chunkCount + ".jsonl";
//                try (FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath)) {
//                    fileOutputStream.write(buffer, 0, bytesRead);
//                }
//                chunkCount++;
//            }
//        }catch (Exception e){
//            log.info("解压文件异常");
//            e.printStackTrace();
//        }
//    }

    public static void zstdFiles(String zstdFilePath, String localFilePath) {
        int chunkSize = 1024 * 1024 * 1024; // 分片大小，默认1GB

        try (InputStream inputStream = new FileInputStream(zstdFilePath);
             ZstdInputStream zstdInputStream = new ZstdInputStream(inputStream);
             BufferedReader reader = new BufferedReader(new InputStreamReader(zstdInputStream, StandardCharsets.UTF_8))) {

            String line;
            int chunkCount = 1;
            long currentSize = 0;

            // 文件输出流，用于写入分块内容
            FileOutputStream fileOutputStream = null;
            BufferedWriter writer = null;

            while ((line = reader.readLine()) != null) {
                if (fileOutputStream == null) {
                    String outputFilePath = localFilePath + "_" + chunkCount + ".jsonl";
                    fileOutputStream = new FileOutputStream(outputFilePath);
                    writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));
                }

                writer.write(line);
                writer.newLine(); // 确保每行后都有换行符

                currentSize += line.getBytes(StandardCharsets.UTF_8).length + 1; // 加1是因为我们写了换行符

                if (currentSize >= chunkSize) {
                    // 当前块大小超过限制，切换到下一个块
                    writer.close();
                    fileOutputStream.close();

                    chunkCount++;
                    currentSize = 0;
                    fileOutputStream = null;
                    writer = null;
                }
            }

            // 确保最后的文件被正确关闭
            if (writer != null) {
                writer.close();
                fileOutputStream.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("解压文件异常");
        }
    }
}
