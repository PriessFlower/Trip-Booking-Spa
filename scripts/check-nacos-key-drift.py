#!/usr/bin/env python3
"""核对 Nacos 键清单的漂移，即 PROJECT.md 附录 A.2 的可执行形态。

A.2 把 config/nacos/trip-booking-spa.yaml.example 定为唯一核对基准，但此前只有一行
注释、没有可执行形态。结果该文件的 7 个 cache.price.* 键从引入那天起就少缩进两格
（挂成了 cache.*），代码一律读不到、也无任何提示，历经三个提交周期无人发现（issue #98）。

两项检查：

1. 【离线，始终执行，有差异即失败】example ↔ 代码实际读取的键。
   "该登记进 Nacos 的键" = 代码读取的键 ∩ example 的顶层域 − application*.yml 里声明过的键。
   减去 yml 那部分是因为凭证与基础设施地址由环境变量注入、禁止入 Nacos（§3.5.1、§3.2.1）；
   而运维配置禁止出现在 yml（§3.3.1），故这个减法不会误伤运维键。
   这一项就是能精准抓住 #98 的检查——少缩进两格会让 example 里多出 7 个没人读的死键，
   同时少 7 个代码要读的键，两个方向同时报错。

2. 【需要 Nacos 凭证，在部署流水线执行】example ↔ 目标环境 Nacos 的键清单。
   按 A.2 的处置表给出方向。只比键路径，不比取值——值是各环境自有的（§3.2.2）。

用法：
    python3 scripts/check-nacos-key-drift.py                  # 只做检查 1（离线）
    NACOS_SERVER_ADDR=host:port NACOS_NAMESPACE=prod \\
    NACOS_USERNAME=... NACOS_PASSWORD=... \\
    python3 scripts/check-nacos-key-drift.py --with-nacos     # 检查 1 + 检查 2

退出码 0 = 无键差异；1 = 有差异；2 = 声明了 --with-nacos 但取不到 Nacos 配置。
"""
import argparse
import glob
import json
import os
import re
import sys
import urllib.parse
import urllib.request

# 键名可能带引号与方括号：限流桶键写成 "[GLOBAL_LIMIT:...]"——括号是 Spring 绑定的要求
# （裸键的冒号会被宽松绑定吃掉，见 RateLimitKeyBindingTest）。不认它们会让整段被跳过，
# 2026-08-25 因此漏掉了 21 个限流键、把父键误报成"未登记"。
KEY_LINE = re.compile(r"^(\s*)(\"?\[?[A-Za-z0-9_.:\-\]]+\"?)\s*:(.*)$")
# @Value("${a.b.c:default}") 与 environment.getProperty("a.b.c", ...)
VALUE_REF = re.compile(r"\$\{([a-zA-Z0-9_.\-]+)")
GET_PROPERTY = re.compile(r"getProperty\(\"([a-zA-Z0-9_.\-]+)")
# @ConfigurationProperties(prefix = "x") 的字段也是读取点。只认 @Value/getProperty 会把这类类的
# 键全判成死键——2026-08-25 限流配置改用它之后 CI 就是这么红的。transient 字段跳过：那是过渡期
# 的兼容字段，读的是别的键。
CONFIG_PROPS = re.compile(r"@ConfigurationProperties\s*\(\s*prefix\s*=\s*\"([a-zA-Z0-9_.\-]+)\"")
PRIVATE_FIELD = re.compile(
    r"^\s*private\s+(?!transient)(?:volatile\s+)?[\w<>,\s\[\]]+?\s+(\w+)\s*[;=]", re.MULTILINE)


# 映射型前缀：这些键的"子项"是<b>值</b>而不是键位。ratelimit.qps 下面挂的是限流桶名，
# 代码把整张 map 一次读进来，不会逐个 @Value 读——若按"每个叶子都要被代码读到"去判，
# 它们会全部被误报成死键。
#
# 这里刻意用白名单而不是通用规则：通用放行会让"漏配一个桶"也检不出来（我 2026-08-25 试过，
# 删掉一个桶脚本照样说通过）。窄白名单至少让豁免范围是显式的。
#
# 这些叶子由别处守：桶之和不超接口桶由 RateLimitProperties 加载时校验并告警，
# 艺龙三路用途桶是否登记由 ElongRefreshRateLimitOwnershipTest 断言。
# 残留缺口：其他家的桶漏配只会静默回落 default-qps，暂无自动检查（欠账）。
MAP_VALUED_PREFIXES = ("ratelimit.qps",)


def under_map_valued(key):
    return any(key == p or key.startswith(p + ".") for p in MAP_VALUED_PREFIXES)


def camel_to_kebab(name):
    return re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name).lower()


def leaf_paths(text):
    """按缩进解析出叶子键路径集合。

    两侧都是同一份 yaml 形态（只有映射、无列表、无多行标量），故不引入 PyYAML
    依赖——部署 runner 上不保证装了它。
    """
    paths = set()
    stack = []  # [(indent, name)]
    for raw in text.split("\n"):
        line = raw.rstrip()
        if not line.strip() or line.strip().startswith("#"):
            continue
        m = KEY_LINE.match(line)
        if not m:
            continue
        indent, name, rest = len(m.group(1)), m.group(2), m.group(3).strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        # 行内注释不算取值：`foo:   # 说明` 仍是父节点
        value = rest.split("#", 1)[0].strip() if rest else ""
        path = ".".join([n for _, n in stack] + [name])
        if value:
            paths.add(path)
        else:
            stack.append((indent, name))
    return paths


def code_keys(source_root):
    keys = set()
    for path in glob.glob(os.path.join(source_root, "**", "*.java"), recursive=True):
        with open(path, encoding="utf-8") as f:
            text = f.read()
        keys.update(VALUE_REF.findall(text))
        keys.update(GET_PROPERTY.findall(text))
        for prefix in CONFIG_PROPS.findall(text):
            for field in PRIVATE_FIELD.findall(text):
                keys.add(prefix + "." + camel_to_kebab(field))
    return keys


def yml_keys(resource_root):
    keys = set()
    for path in sorted(glob.glob(os.path.join(resource_root, "application*.yml"))):
        with open(path, encoding="utf-8") as f:
            keys.update(leaf_paths(f.read()))
    return keys


def load_pending_removal(path):
    """待从 Nacos 清理的残留键：代码已删、Nacos 里还留着。

    登记在案而非一律放行——每个条目都要写清为什么，且键一旦真的从 Nacos 消失，
    检查会要求删掉对应行，清单因此不会腐烂成永久豁免名单。
    """
    if not path or not os.path.exists(path):
        return set()
    keys = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if line:
                keys.add(line)
    return keys


def fetch_nacos(addr, namespace, username, password, data_id, group):
    token = ""
    if username:
        body = urllib.parse.urlencode({"username": username, "password": password or ""}).encode()
        with urllib.request.urlopen(f"http://{addr}/nacos/v1/auth/login", data=body, timeout=15) as resp:
            token = json.loads(resp.read().decode()).get("accessToken", "")
    query = urllib.parse.urlencode({
        "dataId": data_id, "group": group, "tenant": namespace or "", "accessToken": token,
    })
    with urllib.request.urlopen(f"http://{addr}/nacos/v1/cs/configs?{query}", timeout=15) as resp:
        return resp.read().decode()


def check_example_vs_code(example_keys, source_root, resource_root):
    """检查 1：离线，example 与代码必须一一对应。返回 True 表示通过。"""
    # 映射型前缀的叶子是值不是键位，两侧都从本检查中排除（见 MAP_VALUED_PREFIXES）
    example_keys = {k for k in example_keys if not under_map_valued(k)}
    domains = {k.split(".")[0] for k in example_keys}
    in_yml = yml_keys(resource_root)
    scoped = {k for k in code_keys(source_root)
              if k.split(".")[0] in domains and k not in in_yml and not under_map_valued(k)}

    dead = sorted(example_keys - scoped)
    missing = sorted(scoped - example_keys)

    print(f"[检查1 example↔代码] example={len(example_keys)} 应登记={len(scoped)} "
          f"死键={len(dead)} 未登记={len(missing)}")
    for key in dead:
        if key in in_yml:
            print(f"  ✗ {key}：example 与 application*.yml 同时声明。yml 会覆盖 Nacos，"
                  f"改 Nacos 不生效（违反 §3.4.2）")
        else:
            print(f"  ✗ {key}：example 里有，但代码里没有任何地方读它——死键。"
                  f"多半是层级写错了（issue #98），或该键已废弃未清理")
    for key in missing:
        print(f"  ✗ {key}：代码在读，但 example 未登记。example 是键清单的唯一出处"
              f"（§3.7.6），漏登记会让附录 A 的核对失效")
    return not dead and not missing


def check_example_vs_nacos(example_keys, args):
    """检查 2：与目标环境 Nacos 比对。返回 (通过, 是否取到配置)。"""
    addr = os.environ.get("NACOS_SERVER_ADDR")
    if not addr:
        print("[检查2 example↔Nacos] 缺少 NACOS_SERVER_ADDR", file=sys.stderr)
        return False, False
    try:
        text = fetch_nacos(addr, os.environ.get("NACOS_NAMESPACE"),
                           os.environ.get("NACOS_USERNAME"), os.environ.get("NACOS_PASSWORD"),
                           args.data_id, args.group)
    except Exception as exc:  # 取不到必须失败，不能静默放行
        print(f"[检查2 example↔Nacos] 无法取得配置（{addr} {args.data_id}）：{exc}", file=sys.stderr)
        return False, False
    if not text.strip():
        print(f"[检查2 example↔Nacos] Nacos 返回空配置（{addr} {args.data_id}）", file=sys.stderr)
        return False, False

    remote = leaf_paths(text)
    pending = load_pending_removal(args.pending_removal)
    # 已从 Nacos 里消失的待清理键：清单必须跟着收缩，否则它会腐烂成一张永久豁免名单
    vanished = sorted(pending - remote)
    for key in vanished:
        print(f"  ✗ {key}：已登记为待清理，但 Nacos 里已经没有它了 → "
              f"从 {args.pending_removal} 删掉这一行")
    only_local = sorted(example_keys - remote)
    only_remote = sorted(remote - example_keys - pending)
    for key in sorted((remote - example_keys) & pending):
        print(f"  · {key}：Nacos 残留、代码已删，已登记为待清理（{args.pending_removal}）")
    print(f"[检查2 example↔Nacos] example={len(example_keys)} Nacos={len(remote)} "
          f"文件有Nacos无={len(only_local)} Nacos有文件无={len(only_remote)}")
    for key in only_remote:
        print(f"  ✗ {key}：Nacos 在跑但 example 未记录 → 补进 example（附录 A.2）")
    for key in only_local:
        mark = "!" if args.require_nacos_complete else "·"
        print(f"  {mark} {key}：example 有、Nacos 无 → 该键当前跑在代码兜底默认值上；"
              f"要调它必须先补进 Nacos（§3.2.2、附录 A.2）")
    if only_remote or vanished:
        return False, True
    if only_local and args.require_nacos_complete:
        return False, True
    return True, True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--example", default="config/nacos/trip-booking-spa.yaml.example")
    parser.add_argument("--source-root", default="src/main/java")
    parser.add_argument("--resource-root", default="src/main/resources")
    parser.add_argument("--data-id", default="trip-booking-spa.yaml")
    parser.add_argument("--group", default="DEFAULT_GROUP")
    parser.add_argument("--with-nacos", action="store_true", help="同时执行检查 2")
    parser.add_argument("--pending-removal", default="config/nacos/pending-removal.txt",
                        help="待从 Nacos 清理的残留键清单")
    parser.add_argument("--require-nacos-complete", action="store_true",
                        help="把「example 有、Nacos 无」也算失败。待运维把键补齐后再开启")
    args = parser.parse_args()

    with open(args.example, encoding="utf-8") as f:
        example_keys = leaf_paths(f.read())

    ok = check_example_vs_code(example_keys, args.source_root, args.resource_root)

    if args.with_nacos:
        nacos_ok, reachable = check_example_vs_nacos(example_keys, args)
        if not reachable:
            return 2
        ok = ok and nacos_ok

    print("键清单核对：通过" if ok else "键清单核对：不通过")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
