//package com.bingo.hotel.spa.intl.core.api.expedia.utils;
//
///**
// * 特定请求类.
// *
// * @author : hanJH
// * @version : 1.0 2024/08/16
// * @since : 1.0
// **/
//public class PropertyContentCall {
//
//    // Path
//    private static final String PROPERTY_CONTENT_PATH = "v3/properties/content";
//
//    // Headers
//    private static final String LINK = "Link";
//    private static final String PAGINATION_TOTAL_RESULTS = "Pagination-Total-Results";
//
//    // Query parameters keys
//    private static final String LANGUAGE = "language";
//    private static final String SUPPLY_SOURCE = "supply_source";
//    private static final String COUNTRY_CODE = "country_code";
//    private static final String CATEGORY_ID_EXCLUDE = "category_id_exclude";
//    private static final String TOKEN = "token";
//    private static final String INCLUDE = "include";
//
//    // Call parameters
//    private final RapidClient client;
//    private final String language;
//    private final String supplySource;
//    private final List<String> countryCodes;
//    private final List<String> categoryIdExcludes;
//
//    private String token;
//
//    public PropertyContentCall(RapidClient client, String language, String supplySource,
//                               List<String> countryCodes, List<String> categoryIdExcludes) {
//        this.client = client;
//        this.language = language;
//        this.supplySource = supplySource;
//        this.countryCodes = countryCodes;
//        this.categoryIdExcludes = categoryIdExcludes;
//    }
//
//    public Stream<RapidPropertyContent> stream() {
//        return Stream.generate(() -> {
//                    synchronized (this) {
//                        // Make the call to Rapid.
//                        final Response response = client.get(PROPERTY_CONTENT_PATH, queryParameters());
//
//                        // Read the response to return.
//                        final Map<String, RapidPropertyContent> propertyContents = response.readEntity(new GenericType<>() { });
//
//                        // Store the token for pagination if we got one.
//                        token = getTokenFromLink(response.getHeaderString(LINK));
//
//                        return propertyContents;
//                    }
//                })
//                .takeWhile(MapUtils::isNotEmpty)
//                .map(Map::values)
//                .flatMap(Collection::stream);
//    }
//
//    public Integer size() {
//        // Make the call to Rapid.
//        final MultivaluedMap<String, String> queryParameters = queryParameters();
//        queryParameters.putSingle(INCLUDE, "property_ids");
//        final Response response = client.get(PROPERTY_CONTENT_PATH, queryParameters);
//
//        // Read the size to return.
//        final Integer size = Integer.parseInt(response.getHeaderString(PAGINATION_TOTAL_RESULTS));
//
//        // Close the response since we're not reading it.
//        response.close();
//
//        return size;
//    }
//
//    private MultivaluedMap<String, String> queryParameters() {
//        final MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
//
//        if (token != null) {
//            queryParams.putSingle(TOKEN, token);
//        } else {
//            // Add required parameters
//            queryParams.putSingle(LANGUAGE, language);
//            queryParams.putSingle(SUPPLY_SOURCE, supplySource);
//
//            // Add optional parameters
//            if (CollectionUtils.isNotEmpty(countryCodes)) {
//                queryParams.put(COUNTRY_CODE, countryCodes);
//            }
//            if (CollectionUtils.isNotEmpty(categoryIdExcludes)) {
//                queryParams.put(CATEGORY_ID_EXCLUDE, categoryIdExcludes);
//            }
//        }
//
//        return queryParams;
//    }
//
//    private String getTokenFromLink(String linkHeader) {
//        if (StringUtils.isEmpty(linkHeader)) {
//            return null;
//        }
//
//        final int startOfToken = linkHeader.indexOf("=") + 1;
//        final int endOfToken = linkHeader.indexOf(">");
//
//        return linkHeader.substring(startOfToken, endOfToken);
//    }
//}
