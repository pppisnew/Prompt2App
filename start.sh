#!/usr/bin/env bash
# =================================================================
# Prompt2App 后端启动脚本
#
# 用法：
#   ./start.sh                 # 默认 dev profile，跳过测试
#   ./start.sh dev             # dev profile
#   ./start.sh test            # test profile
#   ./start.sh prod            # prod profile（要求所有敏感值经 OS env 注入）
#   ./start.sh local           # local profile（向后兼容旧 application-local.yml）
#   ./start.sh dev --build     # 强制 mvn clean package 后启动
#   ./start.sh dev --jar       # 跳过 mvn 直接 java -jar 启动 target 已存在的 jar
#
# 配置加载优先级（高 → 低，参见 ADR-0010）：
#   JVM -D > CLI -- > OS env > .env > application-{profile}.yml > application.yml
# =================================================================

set -euo pipefail

# -------- 路径 --------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# -------- 入参解析 --------
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 0
fi

PROFILE="${1:-dev}"
shift || true
MODE="run"   # run | build | jar
for arg in "$@"; do
  case "$arg" in
    --build) MODE="build" ;;
    --jar)   MODE="jar" ;;
    *) echo "[!] 未知参数: $arg" >&2; exit 2 ;;
  esac
done

# 校验 profile
case "$PROFILE" in
  dev|test|prod|local) ;;
  *) echo "[!] 非法 profile: ${PROFILE}（合法值: dev / test / prod / local）" >&2; exit 2 ;;
esac

# -------- 颜色输出 --------
if [[ -t 1 ]]; then
  BLUE='\033[0;34m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; RED='\033[0;31m'; NC='\033[0m'
else
  BLUE=''; GREEN=''; YELLOW=''; RED=''; NC=''
fi
log()  { printf "${BLUE}[start.sh]${NC} %s\n" "$*"; }
ok()   { printf "${GREEN}[start.sh]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[start.sh]${NC} %s\n" "$*" >&2; }
die()  { printf "${RED}[start.sh]${NC} %s\n" "$*" >&2; exit 1; }

# -------- 1. JDK 21 强制（Lombok 不支持 JDK 25+）--------
JDK21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
if [[ -d "$JDK21_HOME" ]]; then
  export JAVA_HOME="$JDK21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  ok "JAVA_HOME = $JAVA_HOME"
elif [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
  warn "未找到 Homebrew openjdk@21，沿用现有 JAVA_HOME=$JAVA_HOME"
else
  die "未找到 JDK 21（期望路径 $JDK21_HOME）。请先 brew install openjdk@21"
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F'[".]' '/version/ {print $2; exit}')"
[[ "$JAVA_MAJOR" == "21" ]] || warn "当前 Java 主版本为 $JAVA_MAJOR，建议用 JDK 21（Lombok 在 25+ 上注解处理失败）"

# -------- 2. 检查 .env --------
if [[ "$PROFILE" != "test" && "$PROFILE" != "prod" ]]; then
  if [[ ! -f .env ]]; then
    if [[ -f .env.example ]]; then
      warn ".env 不存在；将仅使用 application.yml 中的 \${VAR:default} 兜底值"
      warn "如需注入真实配置，运行：cp .env.example .env 并填值"
    else
      warn ".env 与 .env.example 都不存在，跳过 dotenv 加载"
    fi
  else
    ok ".env 已加载（spring-dotenv 自动注入）"
  fi
fi

if [[ "$PROFILE" == "prod" ]]; then
  warn "prod profile：所有敏感值必须由 OS env / Secret Manager 注入（脚本不读 .env）"
fi

# -------- 3. 启动模式分派 --------
log "Profile: $PROFILE"
log "模式:    $MODE"

case "$MODE" in
  build)
    log "执行 mvn clean package -DskipTests ..."
    mvn -q clean package -DskipTests
    ok "构建完成"
    MODE="jar"   # build 完接 jar 启动
    ;;
esac

case "$MODE" in
  run)
    log "spring-boot:run  (mvn) ..."
    exec mvn -q spring-boot:run \
      -Dspring-boot.run.profiles="$PROFILE" \
      -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"
    ;;
  jar)
    JAR_FILE="$(ls -1t target/prompt2app-*.jar 2>/dev/null | head -n1 || true)"
    [[ -n "$JAR_FILE" ]] || die "未找到 target/prompt2app-*.jar；先运行 ./start.sh $PROFILE --build"
    ok "Launching $JAR_FILE"
    exec java -Dfile.encoding=UTF-8 \
              -Dspring.profiles.active="$PROFILE" \
              -jar "$JAR_FILE"
    ;;
esac
