# Platform Nginx

`spa.haowan2000.com` 的公网入口。Nginx 运行在独立网关主机，静态文件由
Nginx 提供，只有 `/bff/**` 会通过私网代理到 `172.21.32.4:8080`。
`haowan2000.com` 与 `www.haowan2000.com` 保留给后续渠道流量入口，不在本
虚拟主机中处理；未知 Host 由默认虚拟主机直接拒绝。

## 网络边界

- 公网业务流量只进入 edge 主机的 TCP 80/443。
- Elasticsearch 仅监听 edge 主机私网地址的 9200，不经 Nginx 暴露。
- `trip-offline:8080` 只接受 edge 安全组来源；应用需要监听 `0.0.0.0`，
  但不得在其安全组中向公网开放 8080。
- `haowan2000.com`/`www` 的渠道入口解析独立维护，不能指向此 SPA 虚拟主机。

## 持久化目录

部署文件位于主机 `/opt/nginx`：

- `compose.yml`、`nginx.conf`、`conf.d/`：运行配置
- `www/releases/<release-id>/`：不可变前端构建产物
- `www/current`、`www/previous`：当前版与上一版的相对软链接，用于原子发布/回滚
- `certbot/conf/`：TLS 证书
- `certbot/www/`：ACME HTTP-01 challenge
- `logs/`：访问日志与错误日志

这些目录全部是宿主机 bind mount，删除或重建容器不会丢失内容。

## 运维检查

```bash
cd /opt/nginx
docker compose config
docker compose run --rm nginx nginx -t
docker compose up -d
curl -H 'Host: spa.haowan2000.com' http://127.0.0.1/_health
```

## 前端发布与回滚

生产构建必须关闭 source map，并把静态资源固定到不可变的 release 路径：

```bash
REL='<UTC时间>-<源码摘要>'
PUBLIC_URL="/releases/${REL}" GENERATE_SOURCEMAP=false npm run build
```

只把 `build/` 制品发布到 `/opt/nginx/www/releases/${REL}`；校验摘要后，以
相对软链接原子切换 `current`，并让 `previous` 指向原版本。回滚时交换这两个
链接即可，不需要清空线上目录，也不需要重启 Nginx。

`spa.haowan2000.com` 的 A 记录生效后，先通过预留的 HTTP-01 目录签发证书，
再开放 443 并将普通 HTTP 请求重定向到 HTTPS。80 端口仅保留 `/_health`、
ACME challenge 和固定域名的 HTTPS 跳转。

证书由宿主机定时任务运行固定版本的 Certbot 容器续期。续期成功后先执行
`nginx -t`，再向 Nginx 发送 reload；Certbot 容器不挂载 Docker socket，Nginx
对证书目录始终保持只读挂载。首次部署后必须执行一次 `certbot renew --dry-run`
验证自动续期链路。

定时任务模板位于 `systemd/spa-certbot-renew.{service,timer}`。安装到
`/etc/systemd/system/` 后执行：

```bash
systemctl daemon-reload
systemctl enable --now spa-certbot-renew.timer
systemctl list-timers spa-certbot-renew.timer
```

仓库不保存证书私钥、运行日志或真实前端制品。当前入口不启用 Basic Auth，
因此 `/bff/**` 也可由公网匿名访问；正式承载渠道流量或真实旅客信息前，必须在
BFF 内实现用户鉴权、订单归属校验和取消操作授权。
