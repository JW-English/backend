#!/usr/bin/env python3
"""중간 CSV → 적재용 SQL.

사용:
    python3 build_seed.py --input data/intermediate.csv --level INTERMEDIATE \
        --generated data/generated.jsonl > seed.sql
    psql ... -f seed.sql

정규화도 여기서 한다. 별도 단계로 나누면 파일만 늘고 얻는 게 없다.

핵심은 중복 표제어 처리다. 복습 단원(DAY 59·60)에 앞 단원 단어가 다시 나오는데
words 에 (headword, meaning_ko) 유니크 제약이 있어 그대로 넣으면 막힌다.
단어는 1행만 만들고 word_day_items 로 여러 DAY 에 연결한다.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

# 뜻 구분자. ';' 가 뜻 단위, ',' 는 같은 뜻 안의 유의어다.
# "진보, 발전; 진전, 진행" → ["진보, 발전", "진전, 진행"]
MEANING_SPLIT = re.compile(r"\s*;\s*")


def quote(value) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def normalize_headword(word: str) -> str:
    """표제어 정규화. 소문자화는 하지 않는다 — 고유명사가 섞일 수 있다."""
    return re.sub(r"\s+", " ", word).strip()


def to_meanings_json(meanings) -> str:
    """뜻 → jsonb 배열.

    generate.py 결과는 이미 [{"pos": "v.", "ko": "..."}] 형태다.
    CSV 에서 온 문자열이면 ';' 로 잘라 ko 만 채운다.
    """
    if isinstance(meanings, list):
        parts = [m for m in meanings if (m.get("ko") or "").strip()]
    else:
        parts = [{"ko": p.strip()} for p in MEANING_SPLIT.split(meanings) if p.strip()]
    if not parts:
        return "NULL"
    return quote(json.dumps(parts, ensure_ascii=False)) + "::jsonb"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--level", required=True,
                        choices=["BEGINNER", "INTERMEDIATE", "ADVANCED"])
    parser.add_argument("--day-prefix", default="", help="DAY 제목 접두어 (예: '베이직')")
    parser.add_argument("--generated", help="generate.py 결과 JSONL. CSV 의 뜻보다 우선한다")
    args = parser.parse_args()

    # 생성 결과가 있으면 그쪽을 쓴다. 예문은 여기에만 있다
    generated: dict[str, dict] = {}
    if args.generated:
        for line in Path(args.generated).read_text(encoding="utf-8").splitlines():
            if line.strip():
                row = json.loads(line)
                generated[row["headword"]] = row

    rows = list(csv.DictReader(Path(args.input).open(encoding="utf-8")))
    if not rows:
        print("입력이 비어 있습니다", file=sys.stderr)
        raise SystemExit(1)

    # ── 1) 표제어 단위로 합친다 (중복 제거)
    by_word: dict[str, dict] = {}
    placements: list[tuple[str, int, int]] = []  # (headword, day_no, sort_order)
    conflicts: list[str] = []

    for r in rows:
        word = normalize_headword(r["headword"])
        if not word:
            continue

        day_no = int(r["day_no"])
        placements.append((word, day_no, int(r["sort_order"] or 0)))

        gen = generated.get(word)
        if gen:
            # 생성 결과는 표제어당 하나뿐이라 아래 충돌 병합을 탈 일이 없다
            by_word[word] = {
                "meaning_ko": gen["meaning_ko"].strip(),
                "meanings": gen["meanings"],
                "example_en": (gen.get("example_en") or "").strip(),
                "example_ko": (gen.get("example_ko") or "").strip(),
                "type": (r.get("word_type") or "").strip(),
            }
            continue

        meaning_ko = (r.get("meaning_ko") or "").strip()
        meanings = (r.get("meanings") or "").strip() or meaning_ko

        prev = by_word.get(word)
        if prev is None:
            by_word[word] = {
                "meaning_ko": meaning_ko,
                "meanings": meanings,
                "example_en": "",
                "example_ko": "",
                "type": (r.get("word_type") or "").strip(),
            }
            continue

        # 같은 단어가 DAY 마다 뜻이 다르게 실린 경우가 있다 (복습 단원에서 축약).
        # 짧은 쪽을 쓰면 뜻이 사라지므로 긴 쪽을 남긴다
        if isinstance(prev["meanings"], list):
            continue  # 이미 생성 결과로 채운 단어

        if meaning_ko and meaning_ko != prev["meaning_ko"]:
            conflicts.append(f"{word}: {prev['meaning_ko']!r} vs {meaning_ko!r}")
            if len(meaning_ko) > len(prev["meaning_ko"]):
                prev["meaning_ko"] = meaning_ko
        if len(meanings) > len(prev["meanings"]):
            prev["meanings"] = meanings

    missing = [w for w, v in by_word.items() if not v["meaning_ko"]]
    no_example = [w for w, v in by_word.items() if not v["example_en"]]

    # ── 2) DAY 목록
    day_titles: dict[int, str] = {}
    for r in rows:
        day_no = int(r["day_no"])
        title = (r.get("day_title") or "").strip()
        if title:
            day_titles[day_no] = title
        day_titles.setdefault(day_no, "")

    # ── 3) SQL
    print("-- 생성물입니다. build_seed.py 로 다시 만들 수 있습니다.")
    print(f"-- 입력: {args.input} · 레벨 {args.level}")
    print(f"-- 단어 {len(by_word)} · DAY {len(day_titles)} · 배치 {len(placements)}")
    print("BEGIN;")

    print("\n-- DAY")
    for day_no in sorted(day_titles):
        title = day_titles[day_no] or f"{args.day_prefix}DAY {day_no:02d}".strip()
        print(
            f"INSERT INTO word_days (level, day_no, title) "
            f"VALUES ({quote(args.level)}, {day_no}, {quote(title)}) "
            f"ON CONFLICT (level, day_no) DO UPDATE SET title = EXCLUDED.title;"
        )

    print("\n-- 단어")
    for word in sorted(by_word):
        v = by_word[word]
        tags = "ARRAY[" + quote(v["type"]) + "]" if v["type"] else "NULL"
        print(
            f"INSERT INTO words (headword, meaning_ko, meanings, example_en, example_ko, tags) "
            f"VALUES ({quote(word)}, {quote(v['meaning_ko'])}, {to_meanings_json(v['meanings'])}, "
            f"{quote(v['example_en'])}, {quote(v['example_ko'])}, {tags}) "
            f"ON CONFLICT (headword, meaning_ko) DO UPDATE SET "
            f"meanings = EXCLUDED.meanings, example_en = EXCLUDED.example_en, "
            f"example_ko = EXCLUDED.example_ko, tags = EXCLUDED.tags;"
        )

    print("\n-- DAY 편성")
    # 같은 단어가 같은 DAY 에 두 번 오면 PK 충돌이므로 미리 접는다
    seen_pairs = set()
    for word, day_no, sort_order in placements:
        if (word, day_no) in seen_pairs:
            continue
        seen_pairs.add((word, day_no))
        print(
            f"INSERT INTO word_day_items (day_id, word_id, sort_order) "
            f"SELECT d.id, w.id, {sort_order} FROM word_days d, words w "
            f"WHERE d.level = {quote(args.level)} AND d.day_no = {day_no} "
            f"AND w.headword = {quote(word)} "
            f"ON CONFLICT (day_id, word_id) DO UPDATE SET sort_order = EXCLUDED.sort_order;"
        )

    print("\nCOMMIT;")

    # ── 4) 사람이 봐야 할 것
    print(f"\n✅ 단어 {len(by_word)} · DAY {len(day_titles)} · 배치 {len(seen_pairs)}", file=sys.stderr)
    if missing:
        print(f"⚠️  뜻이 비어 적재되지 않을 단어 {len(missing)}개", file=sys.stderr)
        for w in missing[:10]:
            print(f"     {w}", file=sys.stderr)
        if len(missing) > 10:
            print(f"     … 외 {len(missing) - 10}개", file=sys.stderr)
    if no_example:
        print(f"⚠️  예문이 없는 단어 {len(no_example)}개 (generate.py 를 더 돌리세요)", file=sys.stderr)
        for w in no_example[:10]:
            print(f"     {w}", file=sys.stderr)
        if len(no_example) > 10:
            print(f"     … 외 {len(no_example) - 10}개", file=sys.stderr)
    if conflicts:
        print(f"⚠️  같은 단어에 다른 뜻 {len(conflicts)}건 (긴 쪽을 채택했습니다)", file=sys.stderr)
        for c in conflicts[:5]:
            print(f"     {c}", file=sys.stderr)
        if len(conflicts) > 5:
            print(f"     … 외 {len(conflicts) - 5}건", file=sys.stderr)


if __name__ == "__main__":
    main()
