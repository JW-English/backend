#!/usr/bin/env bash
# DB 백업 → Cloudflare R2. cron 으로 매일 돌린다.
#
#   0 3 * * * /opt/jungwoon/deploy/backup.sh >> /var/log/jungwoon-backup.log 2>&1
#
# 복구:
#   ./backup.sh restore 2026-08-11
#
# 백업은 복구해본 적이 있을 때만 백업이다. 배포 직후 한 번, 이후 분기마다
# restore 를 빈 DB 에 걸어보고 앱이 뜨는지까지 확인한다.

set -euo pipefail

cd "$(dirname "$0")"
set -a; source .env; set +a

: "${DB_USERNAME:?}" "${STORAGE_ENDPOINT:?}" "${BACKUP_BUCKET:?}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
# 미디어 버킷을 같이 쓰는 경우 프리픽스로 분리한다.
# 전용 버킷을 만들면 BACKUP_PREFIX 를 비우면 된다
PREFIX="${BACKUP_PREFIX:-}"

# 백업 전용 R2 토큰이 있으면 그걸 쓴다. 없으면 미디어용으로 대체한다.
# 분리해두면 미디어 토큰이 새어도 백업은 지워지지 않고, 반대도 마찬가지다
AK="${BACKUP_ACCESS_KEY:-$STORAGE_ACCESS_KEY}"
SK="${BACKUP_SECRET_KEY:-$STORAGE_SECRET_KEY}"
EP="${BACKUP_ENDPOINT:-$STORAGE_ENDPOINT}"

# aws cli 를 VM 에 설치하지 않는다. 컨테이너로 그때만 띄운다
s3() {
    docker run --rm -i \
        -e AWS_ACCESS_KEY_ID="$AK" \
        -e AWS_SECRET_ACCESS_KEY="$SK" \
        -e AWS_DEFAULT_REGION="${STORAGE_REGION:-auto}" \
        amazon/aws-cli:latest \
        --endpoint-url "$EP" "$@"
}

backup() {
    local stamp; stamp=$(date +%F-%H%M)
    local key="${PREFIX:+$PREFIX/}db/jungwoon-${stamp}.dump.gz"

    echo "[$(date '+%F %T')] 백업 시작 → ${key}"

    # -Fc 는 custom format. pg_restore 로 테이블 단위 복구가 된다.
    # 파일로 떨구지 않고 파이프로 바로 올린다 — VM 디스크를 안 쓴다
    docker exec jungwoon-postgres \
        pg_dump -U "$DB_USERNAME" -d jungwoon -Fc \
        | gzip \
        | s3 s3 cp - "s3://${BACKUP_BUCKET}/${key}"

    local size
    size=$(s3 s3 ls "s3://${BACKUP_BUCKET}/${key}" | awk '{print $3}')
    if [ -z "$size" ] || [ "$size" -lt 10000 ]; then
        echo "❌ 백업이 비어 있거나 너무 작습니다 (${size:-0} bytes)"
        exit 1
    fi
    echo "✅ ${key} · ${size} bytes"

    prune
}

# 오래된 것 정리. R2 수명주기 규칙으로도 되지만, 스크립트에 두면
# 보관 기간이 코드에 남아서 나중에 왜 30일인지 찾을 수 있다
prune() {
    local cutoff; cutoff=$(date -d "${KEEP_DAYS} days ago" +%F 2>/dev/null || date -v-"${KEEP_DAYS}"d +%F)
    s3 s3 ls "s3://${BACKUP_BUCKET}/${PREFIX:+$PREFIX/}db/" | while read -r date_part _ _ name; do
        if [[ "$date_part" < "$cutoff" ]]; then
            echo "  정리: $name"
            s3 s3 rm "s3://${BACKUP_BUCKET}/${PREFIX:+$PREFIX/}db/${name}"
        fi
    done
}

restore() {
    local day="${1:?복구할 날짜를 주세요 (예: 2026-08-11)}"
    local key
    key=$(s3 s3 ls "s3://${BACKUP_BUCKET}/${PREFIX:+$PREFIX/}db/" | grep "jungwoon-${day}" | tail -1 | awk '{print $4}')
    [ -z "$key" ] && { echo "❌ ${day} 백업을 찾지 못했습니다"; exit 1; }

    echo "⚠️  현재 DB 를 ${key} 로 덮어씁니다. 5초 안에 Ctrl-C 로 중단할 수 있습니다."
    sleep 5

    s3 s3 cp "s3://${BACKUP_BUCKET}/${PREFIX:+$PREFIX/}db/${key}" - \
        | gunzip \
        | docker exec -i jungwoon-postgres \
            pg_restore -U "$DB_USERNAME" -d jungwoon --clean --if-exists --no-owner

    echo "✅ 복구 완료. api 를 재시작하세요: docker compose -f docker-compose.prod.yml restart api"
}

case "${1:-backup}" in
    backup)  backup ;;
    restore) restore "${2:-}" ;;
    prune)   prune ;;
    *) echo "사용: $0 [backup|restore <YYYY-MM-DD>|prune]"; exit 1 ;;
esac
