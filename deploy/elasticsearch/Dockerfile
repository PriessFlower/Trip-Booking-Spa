# 官方镜像零插件，中文按 standard 分析器逐字切（「机场」→「机」「场」），
# 酒店名检索必须有中文分词器，故在官方镜像上装 IK。
#
# ⚠️ IK 的版本号必须与 ES 完全一致（9.5.1）。差一位插件直接拒绝加载，ES 会起不来。
# 升级 ES 时这一行必须同步改，且要先确认 release.infinilabs.com 上有对应版本
# ——IK 是第三方插件，通常滞后于 ES 发版。
#
# 在 edge 主机上构建（该机能直连 release.infinilabs.com，实测 200；这条链路不经 GitHub）：
#   cd /opt/elasticsearch && docker build -t trip-elasticsearch:9.5.1-ik .
FROM docker.elastic.co/elasticsearch/elasticsearch:9.5.1
RUN bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-9.5.1.zip
