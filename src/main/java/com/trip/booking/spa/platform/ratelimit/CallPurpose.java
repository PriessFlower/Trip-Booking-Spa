package com.trip.booking.spa.platform.ratelimit;

/**
 * 一次供应商调用的<b>用途</b>。它决定两件事：吃哪个子桶、拿不到许可时等还是走。
 *
 * <p><b>为什么是必填入参而不是可选。</b>同一个接口常有多路消费方——艺龙 {@code hotel.detail}
 * 就有三路（后台刷价、客人点订前的现取现验、上游实时查价）。总桶是我们对供应商的承诺，
 * 内部谁吃多少要分开管；而"分配"只有在<b>没有任何一路能绕过</b>时才成立。把用途做成
 * {@code access} 的必填参数，新增调用路径不声明用途就编译不过——这是它与"记得手写一行
 * acquire"的唯一区别，也是选它的全部理由。
 *
 * <p><b>阻塞与否由用途决定，不由调用点决定。</b>此前是「刷价在业务代码里阻塞、其余在通道层
 * 非阻塞」，同一件事分散在两层，看代码的人得两处都读到才知道自己会不会被挂住。
 */
public enum CallPurpose {

    /** 后台刷价：没有人在等，慢一点没有代价，故阻塞排队而不是失败 */
    REFRESH(false),

    /** 后台静态数据与目录文件摄取：同上 */
    CONTENT(false),

    /** 上游实时查价（绕过缓存那条路）：上游请求在等 */
    LIVE(true),

    /** 验价，含点订前的现取现验：客人在屏幕前等 */
    CHECK_PRICE(true),

    /** 下单、查单、取消：客人在等，且写操作不重试（architecture.md §4.3） */
    ORDER(true);

    private final boolean failFast;

    CallPurpose(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * true=等待上限内拿不到许可即如实失败；false=阻塞排队直到拿到。
     *
     * <p>前台一律快速失败：把客人挂在限流上比告诉他"稍后重试"更糟。后台一律排队：
     * 刷价被限流挡掉会计入失败态而不动缓存（F-5.1），等于凭空造出一次假失败。
     */
    public boolean failFast() {
        return failFast;
    }
}
