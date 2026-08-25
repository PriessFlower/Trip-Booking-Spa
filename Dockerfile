ARG RUNTIME_IMAGE=ccr.ccs.tencentyun.com/priessflower/trip-booking-spa:runtime-base-jre21
FROM ${RUNTIME_IMAGE}

WORKDIR /app

COPY --chown=app:app target/trip-booking-spa-0.0.1.jar /app/app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod

# 时区必须显式钉住，不能跟随基础镜像。刷价把「相对天数」换算成绝对日期
# （LocalDate.now().plusDays(delay)），而 now() 取的是 JVM 默认时区：
#
#   基础镜像未设 TZ 时 JVM 走 UTC，于是北京时间 00:00-08:00 这八小时里，
#   我们眼中的"今天"是北京的"昨天"。2026-08-25 01:00 生产实测：我们刷
#   08-24/25/26，而上游 cursor（启动参数写死 -Duser.timezone=Asia/Shanghai）
#   要的是 08-25/26/27。两头都错——约三分之一额度打在已经过去的日期上
#   （当日无货率 56% vs 次日 87%），而上游要的第三天我们没有。
#
# 取 Asia/Shanghai 而非 UTC：日期口径由"谁在问"和"谁供货"决定，上游 cursor
# 与艺龙都按北京时间。cursor 那侧是显式指定的，本仓此前漏了这一条。
ENV TZ=Asia/Shanghai

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
