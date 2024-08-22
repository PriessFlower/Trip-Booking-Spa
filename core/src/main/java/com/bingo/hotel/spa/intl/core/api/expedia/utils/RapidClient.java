package com.bingo.hotel.spa.intl.core.api.expedia.utils;


import com.alibaba.schedulerx.shade.org.apache.commons.codec.digest.DigestUtils;
import com.expediagroup.sdk.core.plugin.logging.GZipEncoder;

import javax.ws.rs.core.Response;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.text.MessageFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * 基础调用类.
 *
 * @author : hanJH
 * @version : 1.0 2024/08/16
 * @since : 1.0
 **/
public class RapidClient {
    // Base URL
    private static final String RAPID_BASE_URL = "https://api.ean.com";

    // Headers
    private static final String GZIP = "gzip";
    private static final String AUTHORIZATION_HEADER = "EAN APIKey={0},Signature={1},timestamp={2}";

    // HTTP Client
    private static final Client CLIENT = ClientBuilder.newClient().register(GZipEncoder.class);

    private final String apiKey;
    private final String sharedSecret;

    public RapidClient(String apikey, String sharedSecret) {
        this.apiKey = apikey;
        this.sharedSecret = sharedSecret;
    }

    public Response get(String path, MultivaluedMap<String, String> queryParameters) {
        WebTarget webTarget = CLIENT.target(RAPID_BASE_URL).path(path);

        // Add all query parameters from the map to the web target
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            for (String value : entry.getValue()) {
                webTarget = webTarget.queryParam(entry.getKey(), value);
            }
        }

        return webTarget.request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.ACCEPT_ENCODING, GZIP)
                .header(HttpHeaders.AUTHORIZATION, generateAuthHeader())
                .get();
    }

    private String generateAuthHeader() {
        final String timeStampInSeconds = String.valueOf(ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond());
        final String input = apiKey + sharedSecret + timeStampInSeconds;
        final String signature = DigestUtils.sha512Hex(input);

        return MessageFormat.format(AUTHORIZATION_HEADER, apiKey, signature, timeStampInSeconds);
    }
}
