# Monitoring

Prometheus, Grafana and node_exporter run on `trip-prod-platform` (`ins-cez660p7`,
private address 172.21.32.14 — the Nacos host), scraping the SPA over the VPC.
Runtime state lives below `/opt/monitoring` on that host:

- `data/prometheus/`: TSDB, owned by uid 65534; retention 30d and 10 GB, whichever comes first
- `data/grafana/`: `grafana.db`, dashboards created through the UI, owned by uid 472
- `.env`: the Grafana admin credential, mode `0600`; see `.env.example`

All three listeners are bound to the private address only — 9090 (Prometheus),
3000 (Grafana) and 9100 (node_exporter). Do not publish them on the public address:
Grafana speaks plain HTTP, and its login is the only thing in front of every
production metric.

## Access

Grafana is reachable at `https://spa.haowan2000.com/grafana/`, proxied by the edge
Nginx (`deploy/nginx/conf.d/spa.haowan2000.com.conf`), which terminates TLS. The
login page is reachable from any address on purpose: the operator connects through a
proxy whose exit address rotates, so an address allow list locks the operator out
rather than keeping anyone else in. The account is the gate, and `/grafana/login` is
rate limited to 1 r/s (burst 5) so that public reachability does not make guessing
cheap. Dashboard traffic is deliberately outside that limit — one view pulls dozens
of assets and queries.

Two pieces have to agree, and both are easy to forget:

- Grafana runs with `GF_SERVER_ROOT_URL` and `GF_SERVER_SERVE_FROM_SUB_PATH`, so it
  expects to receive the `/grafana` prefix. The Nginx `proxy_pass` therefore carries
  no URI part.
- `sg-p1ogl5st` allows 3000 from the Nginx host's group (`sg-kbp3xjyt`) only.

Do not bother opening 3000 to a workstation address directly: the development
workstation's egress passes 80 and 443 only, so any other port is a black hole
regardless of the security group.

## Alerts

`prometheus/rules/spa.yml` holds 12 rules. There is **no Alertmanager**, so nothing
is delivered anywhere — a firing rule shows up in the Prometheus UI and in Grafana,
and nowhere else. Adding notifications means adding Alertmanager and a channel.

Thresholds are anchored to measured baselines (refresh failure ~0.17%, ELONG price
query ~1.3 s), roughly an order of magnitude above normal, so a firing alert should
mean something actually changed rather than that the number was guessed.

Docker Hub is unreachable from this host, so image references carry the
`mirror.ccs.tencentyun.com/` prefix. There is no need to configure a registry mirror
in `daemon.json`.

## Scrape reachability

A shared VPC does not imply connectivity — private traffic still passes security
groups. Prometheus reaches the SPA only because `sg-2bkpoddf` allows 8080 from
`sg-p1ogl5st`. That group ends with a deny-all rule, so a new ACCEPT rule appended
to the end has no effect; insert it at `PolicyIndex 0`.

## Deploying a change

The host copy is a plain mirror of this directory. Ship it, then apply:

```bash
cd /opt/monitoring
docker compose config -q
docker compose up -d
```

Prometheus is started with `--web.enable-lifecycle`, so a scrape-config change can
be applied without a restart:

```bash
curl -X POST http://172.21.32.14:9090/-/reload
```

Dashboard files are re-read every 30 seconds; no restart is needed for those either.

## Useful checks on the host

```bash
curl -s http://172.21.32.14:9090/api/v1/targets \
  | python3 -c 'import json,sys; [print(t["labels"]["job"], t["health"]) for t in json.load(sys.stdin)["data"]["activeTargets"]]'
curl -s --data-urlencode 'query=up' http://172.21.32.14:9090/api/v1/query
curl -s http://172.21.32.14:3000/api/health
```

## Metric notes

`supplier_io_access_time` and `refresh_round_time` are recorded in **milliseconds**,
not seconds, so the dashboard formats them as `ms`. Micrometer's own timers
(`http_server_requests_seconds`, `jvm_gc_pause_seconds`) are in seconds.
