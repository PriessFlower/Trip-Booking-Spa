package com.trip.booking.spa.gateway.application.cancellation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住取消能力的依赖方向：② 不识 ① 的 JSON。
 *
 * <p>五个能力接口此前全部直接吃 REST DTO（依赖方向倒挂：②依赖①），取消是第一个矫正的
 * 能力面——JSON↔领域的翻译收在 ① 的 CancelMapping，②③只说领域语言。本测试防的是
 * 下一个改动图省事把 {@code rest.dto} 重新 import 回来，让试点静默失效。
 *
 * <p>范围刻意只圈 cancellation 与 domain：其余四个能力尚未解耦（pricing/checkprice/
 * booking/order 仍吃 REST DTO），每解耦一个能力，把它的包加进 DECOUPLED_PACKAGES。
 */
class CancellationLayerBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java/com/trip/booking/spa");

    /** 已完成解耦的 ② 层能力包：不许 import adapter.inbound（rest 请求/DTO 均在其中） */
    private static final List<String> DECOUPLED_PACKAGES = List.of(
            "gateway/application/cancellation");

    @Test
    void decoupledCapabilitiesMustNotImportInboundRest() throws IOException {
        for (String pkg : DECOUPLED_PACKAGES) {
            List<String> offenders = offenders(MAIN.resolve(pkg),
                    "import com.trip.booking.spa.gateway.adapter.inbound");
            assertTrue(offenders.isEmpty(),
                    pkg + " 已解耦，不许回头依赖 ① 的 REST 契约（翻译只在 CancelMapping）：" + offenders);
        }
    }

    /** 领域层是纯模型：对适配层（inbound 与 outbound）零依赖，这是六边形的底线 */
    @Test
    void domainMustNotImportAnyAdapter() throws IOException {
        List<String> offenders = offenders(MAIN.resolve("gateway/domain"),
                "import com.trip.booking.spa.gateway.adapter");
        assertTrue(offenders.isEmpty(),
                "gateway/domain 是纯模型，不许 import 任何 adapter：" + offenders);
    }

    private static List<String> offenders(Path root, String forbiddenImportPrefix) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(forbiddenImportPrefix);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());
        }
    }
}
