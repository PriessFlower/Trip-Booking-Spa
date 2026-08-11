package com.trip.booking.spa.core.api.expedia.config;

import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Expedia 合同档案（profile）：一次问价以哪条车道的身份发出。
 *
 * <p>本项目只有 B2C 与 B2B 两套 profile，两者共用同一把 API key，各由四个参数共同构成
 * 一个整体，<b>不可混用</b>（技术研讨会 PDF p6 逐项参数表）。
 *
 * <p>混用的后果实测如下（test.ean.com，property 11775754，三次一致）：Expedia 对四种
 * 组合一律返回 HTTP 200，不报错也不告警；但持 B2C 档案而 {@code sales_channel} 停在 B2B
 * 的 {@code agent_tool} 时，同一间房同一个 rate plan 的含税价比正确取值高约 18%
 * （如 rate 276305852：88.20 对 74.95 CNY），并少返回两条报价。
 * 反向（B2B 档案配 {@code mobile_app}）实测无差异——移动端折扣本就是 B2C 专属。
 *
 * <p>即错配只影响报价、不产生任何可观察信号，故本类在启动期校验四项是否成组，凑不成即拒绝启动。
 *
 * <p>本类是这四个参数的唯一持有者与唯一写入口。此前它们分散于三处：
 * {@code sales_channel} 硬编码在通道层的 {@code QueryProductAccess}，其余三项由适配层的
 * {@code ExpediaPriceServiceImpl} 与状态层的 {@code ExpediaProductMappingService} 各自
 * {@code @Value} 绑定一遍。按 docs/architecture.md §2，供应商语义只应存在于适配层，
 * 通道层仅负责限流／重试／解析／监控。
 *
 * <p>护栏形式（配置不合法即拒绝启动）沿用 {@link ExpediaRapidProperties}，依据 PROJECT.md §3.2.3。
 *
 * <p>车道与价格类型的关系见 docs/expedia/价格类型.md。
 */
@Slf4j
@Component
public class ExpediaContractProfile implements InitializingBean {

    /**
     * EPS 已发放的两套 profile（PDF p6）。四项取值必须整体取自同一套。
     *
     * <p>注意 {@code sales_channel} 的 B2B 取值：kickoff 纪要记为 {@code pc_website}，
     * 技术研讨会 PDF 记为 {@code agent_tool}，以后者为准（PDF 晚于 kickoff，且为逐项参数表）。
     */
    enum Known {
        B2C("B2C_SA_MOD_XSELL_APP", "EAC", "1", "mobile_app"),
        B2B("B2B_SA_PKG_MOD_AGENT", "EAC", "2", "agent_tool");

        private final String partnerPointOfSale;
        private final String billingTerms;
        private final String paymentTerms;
        private final String salesChannel;

        Known(String partnerPointOfSale, String billingTerms, String paymentTerms, String salesChannel) {
            this.partnerPointOfSale = partnerPointOfSale;
            this.billingTerms = billingTerms;
            this.paymentTerms = paymentTerms;
            this.salesChannel = salesChannel;
        }

        boolean matches(String pos, String billing, String payment, String channel) {
            return partnerPointOfSale.equals(pos)
                    && billingTerms.equals(billing)
                    && paymentTerms.equals(payment)
                    && salesChannel.equals(channel);
        }

        String describe() {
            return name() + "(partner_point_of_sale=" + partnerPointOfSale
                    + ", billing_terms=" + billingTerms
                    + ", payment_terms=" + paymentTerms
                    + ", sales_channel=" + salesChannel + ")";
        }
    }

    @Value("${expedia.partner_point_of_sale}")
    private String partnerPointOfSale;
    @Value("${expedia.payment_terms}")
    private String paymentTerms;
    @Value("${expedia.billing_terms}")
    private String billingTerms;
    @Value("${expedia.sales_channel}")
    private String salesChannel;

    /** 取 rate_plan_count；它非车道参数，但同属每次查价必带的固定参数，故一并由本类写入 */
    @Resource
    private ExpediaRapidProperties rapidProperties;

    /** 供 Spring 字段注入 */
    public ExpediaContractProfile() {
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定 */
    ExpediaContractProfile(String partnerPointOfSale, String billingTerms,
                           String paymentTerms, String salesChannel,
                           ExpediaRapidProperties rapidProperties) {
        this.partnerPointOfSale = partnerPointOfSale;
        this.billingTerms = billingTerms;
        this.paymentTerms = paymentTerms;
        this.salesChannel = salesChannel;
        this.rapidProperties = rapidProperties;
    }

    /**
     * 判定四项参数属于哪一套 profile。
     *
     * @throws IllegalStateException 四项凑不成任何一套已知 profile
     */
    static Known resolve(String partnerPointOfSale, String billingTerms,
                         String paymentTerms, String salesChannel) {
        return Arrays.stream(Known.values())
                .filter(known -> known.matches(partnerPointOfSale, billingTerms, paymentTerms, salesChannel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expedia contract parameters do not form a known profile: "
                                + "partner_point_of_sale=" + partnerPointOfSale
                                + ", billing_terms=" + billingTerms
                                + ", payment_terms=" + paymentTerms
                                + ", sales_channel=" + salesChannel
                                + "; the four must be taken as a whole from exactly one of: "
                                + Arrays.stream(Known.values()).map(Known::describe).collect(Collectors.joining(" | "))));
    }

    @Override
    public void afterPropertiesSet() {
        Known profile = resolve(partnerPointOfSale, billingTerms, paymentTerms, salesChannel);
        // 记明当前车道，便于事后从启动日志确认线上跑的是哪一套
        log.info("Expedia contract profile in use: {}", profile.describe());
    }

    /**
     * 查价请求的唯一起手式：所有查价请求都必须由此构造，
     * 以免某一处漏填而与其余请求走上不同车道。
     *
     * <p>调用方只需再补该次请求特有的部分（酒店、日期、人数、币种、{@code sales_environment}）。
     */
    public QueryPriceRequest.QueryPriceRequestBuilder newRequestBuilder() {
        return QueryPriceRequest.builder()
                .partner_point_of_sale(partnerPointOfSale)
                .billing_terms(billingTerms)
                .payment_terms(paymentTerms)
                .sales_channel(salesChannel)
                .rate_plan_count(String.valueOf(rapidProperties.getRatePlanCount()));
    }

    /**
     * 为验价（price_check）链接补齐合同参数：该链接来自查价响应，
     * 不带这些参数时 Expedia 返回 invalid_input。
     *
     * <p>此处<b>有意不追加 {@code sales_channel}</b>：验价链路自接入起就不带该参数且实测通行
     * （PDF 亦仅将其列为 "other important params"，未标必填）。补发属未经验证的行为变更，
     * 应单独实测后再定，不与本次参数收口混做。
     */
    public String appendTo(String priceCheckHref) {
        if (StringUtils.isBlank(priceCheckHref)) {
            return priceCheckHref;
        }
        return new StringBuilder(priceCheckHref)
                .append("&billing_terms=").append(billingTerms)
                .append("&payment_terms=").append(paymentTerms)
                .append("&partner_point_of_sale=").append(partnerPointOfSale)
                .toString();
    }
}
