package com.trip.booking.spa.gateway.domain.product;

import java.util.List;

/**
 * 占用规范串的<b>唯一拼法</b>——productKey 的 {@code o:} 成分与价格缓存键共用它。
 *
 * <p>形如 {@code 2}（两位成人）或 {@code 2-9,4}（两位成人 + 9 岁与 4 岁儿童）。
 *
 * <p><b>为什么必须收成一处</b>：这个串同时出现在三个地方——① 派生 productKey 时；
 * ② 刷价写缓存键时；③ 出价读缓存键时。三处各拼一次，就是"两端靠约定对齐"的老病：
 * cursor 10 家供应商里 6 家没走统一维度，2 间×1 人的多间单写入侧落 {@code a1}、
 * 读取侧找 {@code a2}，**恒定 cache miss**，而那 6 家的 javadoc 还写着「口径一致」。
 * 本类存在的意义就是让那种漂移在结构上无处发生。
 *
 * <p>与改造前的两处实现逐字节等价（艺龙 {@code buildOccupancy}、Expedia 内联拼接），
 * 故不改变任何既有 productKey。
 */
public final class Occupancy {

    private Occupancy() {
    }

    /**
     * @param adults    成人数；{@code null} 或非正数按 1 计（缺人数不该整条不可用）
     * @param childNum  儿童数；为空或 0 时忽略 childAges
     * @param childAges 儿童年龄，按传入顺序拼接（顺序即供应商口径，不排序）
     */
    public static String canonical(Integer adults, Integer childNum, List<Integer> childAges) {
        StringBuilder occupancy = new StringBuilder(String.valueOf(adults == null || adults <= 0 ? 1 : adults));
        if (childNum != null && childNum > 0 && childAges != null && !childAges.isEmpty()) {
            for (int i = 0; i < childAges.size(); i++) {
                occupancy.append(i == 0 ? "-" : ",").append(childAges.get(i));
            }
        }
        return occupancy.toString();
    }

    /** 每间一条，共 {@code roomNum} 条——各家供应商的请求体都要求逐间列出 */
    public static List<String> perRoom(Integer roomNum, Integer adults, Integer childNum, List<Integer> childAges) {
        String one = canonical(adults, childNum, childAges);
        int rooms = roomNum == null || roomNum <= 0 ? 1 : roomNum;
        return java.util.Collections.nCopies(rooms, one);
    }
}
