package com.trip.booking.spa.core.api.expedia.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉死查单 URL 的参数编码。
 *
 * <p>上游单号的字符集不受本服务约束。若不编码，含 {@code &}／{@code =}／空格的单号会产出
 * 非法 URL，该单每次查询都落 INDETERMINATE、永远无法确证——三态契约在这类单号上等于失效。
 * 本用例是实测发现的：未编码时 {@code "bad id &with=junk"} 的查单请求直接失败。
 */
class QueryOrderAccessTest {

    @Test
    void spaceIsEncoded() {
        assertEquals("bad+id", QueryOrderAccess.urlEncode("bad id"));
    }

    /** & 与 = 不编码会被当成参数分隔符，把单号截断成另一个参数 */
    @Test
    void queryDelimitersAreEncoded() {
        assertEquals("a%26b%3Dc", QueryOrderAccess.urlEncode("a&b=c"));
    }

    /** 邮箱的 @ 与 + 同样需要编码，否则 + 会被服务端解回空格 */
    @Test
    void emailSpecialCharsAreEncoded() {
        assertEquals("neo%2Btag%40qq.com", QueryOrderAccess.urlEncode("neo+tag@qq.com"));
    }

    /** 常规单号编码后应保持原样，避免为修边缘情形改变主流程行为 */
    @Test
    void ordinaryOrderIdIsUnchanged() {
        assertEquals("NEO-1786366997", QueryOrderAccess.urlEncode("NEO-1786366997"));
    }

    @Test
    void nullBecomesEmptyRatherThanLiteralNull() {
        assertEquals("", QueryOrderAccess.urlEncode(null));
    }
}
