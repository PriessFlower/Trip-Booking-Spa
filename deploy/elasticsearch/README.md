# Elasticsearch

The production search node runs as a single authenticated TLS-enabled Elasticsearch
container. Runtime state is stored below `/opt/elasticsearch` on the host:

- `data/`: indices
- `logs/`: Elasticsearch logs
- `certs/`: node certificate and public CA certificate
- `ca-private/`: offline CA private key; never mounted into the running container
- `secrets/elastic_password`: bootstrap superuser password, mode `0640` (`root:root`), so the container process (gid 0) can read it

The image is built from the `Dockerfile` in this directory: the official Elasticsearch
image plus the IK Chinese analyzer, whose version must match the server version exactly.
The stock `standard` analyzer splits Chinese per character, which is unusable for
hotel-name search. IK's built-in dictionary covers major cities and international brands
but not Southeast Asian districts — `素坤逸`, `芭提雅`, `卡塔` and `新宿` are still split
per character — so a custom dictionary is still required for our markets.

The container exposes HTTPS only on the host private address `172.21.32.16:9200`.
Port 9300 is not published. Never proxy Elasticsearch through the public Nginx virtual
host or open ports 9200/9300 to the internet.

The initial host has only a 50 GiB system disk. Absolute disk watermarks reserve at
least 10 GiB for the operating system, but a dedicated 100–200 GiB data disk should be
attached before storing material production data.

Useful checks on the host:

```bash
cd /opt/elasticsearch
docker compose config
docker compose ps
curl --cacert certs/ca/ca.crt \
  --user "elastic:$(cat secrets/elastic_password)" \
  https://172.21.32.16:9200/_cluster/health
```
