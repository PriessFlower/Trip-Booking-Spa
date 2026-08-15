package com.trip.booking.spa.gateway.application.routing;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供应商能力注册表：路由与能力发现的唯一入口。
 *
 * <p>此前路由散落在 SpaController 各方法里手拼 bean 名（desc+后缀）再查容器——
 * 能力是隐式的：上游只能靠实际调用来发现某家支不支持某能力（architecture.md §3.1
 * 在册缺口），且拼接约定没有任何启动期校验，bean 名打错一个字母就是静默 404。
 *
 * <p>本类在启动时把 供应商 × 能力 矩阵一次性建好（不可变），此后：
 * <ul>
 *   <li>路由 {@link #find}：查表取 bean，无表项返回 null（语义与旧实现一致——
 *       上游收到"该供应商不支持该操作"，绝不抛异常）</li>
 *   <li>发现 {@link #capabilityMatrix}：上游可预先查询而非试探调用</li>
 * </ul>
 *
 * <p>启动日志打印全矩阵：哪家缺哪个能力一眼可见、可检索（PROJECT.md §6.1）。
 */
@Slf4j
@Component
public class SupplierCapabilityRegistry implements InitializingBean {

    @Resource
    private ApplicationContext applicationContext;

    private Map<SupplierSourceEnum, Map<Capability, Object>> registry = Collections.emptyMap();

    @Override
    public void afterPropertiesSet() {
        Map<SupplierSourceEnum, Map<Capability, Object>> built = new EnumMap<>(SupplierSourceEnum.class);
        for (SupplierSourceEnum supplier : SupplierSourceEnum.values()) {
            Map<Capability, Object> capabilities = new EnumMap<>(Capability.class);
            for (Capability capability : Capability.values()) {
                String beanName = supplier.getDesc() + capability.beanNameSuffix();
                if (applicationContext.containsBean(beanName)) {
                    capabilities.put(capability, applicationContext.getBean(beanName));
                }
            }
            built.put(supplier, Collections.unmodifiableMap(capabilities));
            log.info("能力注册: supplier={}({}) capabilities={}",
                    supplier.getDesc(), supplier.getCode(), capabilities.keySet());
        }
        this.registry = Collections.unmodifiableMap(built);
    }

    /**
     * 路由：取某供应商某能力的实现；不支持返回 null，由调用方回报
     * "该供应商不支持该操作"（与旧 findSupplierService 行为一致）。
     */
    public <T> T find(Integer supplierId, Capability capability, Class<T> serviceType) {
        SupplierSourceEnum supplier = supplierId == null ? null : SupplierSourceEnum.getEnum(supplierId);
        if (supplier == null) {
            return null;
        }
        Object bean = registry.getOrDefault(supplier, Collections.emptyMap()).get(capability);
        return serviceType.isInstance(bean) ? serviceType.cast(bean) : null;
    }

    /** 能力发现：供应商 → 支持的能力集，供 /client/spa/capabilities 端点透出 */
    public Map<String, Object> capabilityMatrix() {
        Map<String, Object> matrix = new LinkedHashMap<>();
        registry.forEach((supplier, capabilities) -> {
            if (!capabilities.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("supplierId", supplier.getCode());
                entry.put("capabilities", capabilities.keySet().stream().map(Enum::name).sorted().toList());
                matrix.put(supplier.getDesc(), entry);
            }
        });
        return matrix;
    }
}
