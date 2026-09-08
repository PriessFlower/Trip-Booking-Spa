-- 2026-09-08 supplier_product_base 加 supplier_display_room_id：展示/归组用的房型号（供应商静态房型号）。
-- 背景：缓存读侧的 room.roomId 由档案表重建（PriceCacheServiceImpl → ProductAttributeReader.toRoom），
-- 取的是身份成分 supplier_room_id（艺龙 = RatePlan.RoomTypeId 销售号）。艺龙静态/物理房型号是外层 Room.RoomId，
-- 两套号大多不等；cursor 按物理号归组，拿到销售号就归不上、报价被丢（同店只有约四成号码碰巧相同）。
-- 实时路径已由 PR #212 改为物理号；缓存路径要靠这一列。可空、纯新增，老代码无感；随每轮刷价 upsert 逐步填满。
ALTER TABLE supplier_product_base
  ADD COLUMN supplier_display_room_id VARCHAR(64) NULL
    COMMENT '展示/归组用的房型号=供应商静态房型号（艺龙=外层 Room.RoomId 物理房型）。与身份成分 supplier_room_id 是两套号；别家同 supplier_room_id。缓存读侧 toRoom 优先用它'
    AFTER supplier_room_id;
