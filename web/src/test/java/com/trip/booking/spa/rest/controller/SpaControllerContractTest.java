package com.trip.booking.spa.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpaControllerContractTest {

    private static final Map<String, String> POST_ROUTES = Map.of(
            "queryPrice", "/price",
            "checkPrice", "/check",
            "booking", "/booking",
            "cancel", "/cancel",
            "orderQuery", "/order",
            "pushPriceAndInventory", "/push/priceAndInventory"
    );

    @Test
    void exposesStableControllerBasePath() {
        RequestMapping mapping = SpaController.class.getAnnotation(RequestMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/client/spa"}, mapping.value());
    }

    @Test
    void preservesLegacyPostRoutesAsControllerEndpoints() {
        for (Map.Entry<String, String> route : POST_ROUTES.entrySet()) {
            Method method = findMethod(route.getKey());
            PostMapping mapping = method.getAnnotation(PostMapping.class);

            assertNotNull(mapping, route.getKey() + " must remain a POST endpoint");
            assertArrayEquals(new String[]{route.getValue()}, mapping.value());
        }
    }

    @Test
    void preservesExpediaHotelLookupRoute() {
        Method method = findMethod("queryExpediaHotelIdByCity");
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/query/expediaHotelIdByCity"}, mapping.value());
    }

    @Test
    void exposesExactlySevenPublicOperations() {
        long operationCount = java.util.Arrays.stream(SpaController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(GetMapping.class))
                .count();

        assertEquals(7, operationCount);
    }

    private Method findMethod(String name) {
        return java.util.Arrays.stream(SpaController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing controller method: " + name));
    }
}
