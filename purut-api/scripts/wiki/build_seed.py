#!/usr/bin/env python3
"""위키 JSON → 적재 SQL.

사용:
    python3 extract.py --input ~/Downloads/영어위키.pdf > data/wiki.json
    python3 build_seed.py --input data/wiki.json > out/wiki.sql
    psql ... -f out/wiki.sql

교재를 다시 뽑아도 같은 결과가 나오도록 upsert 로 만든다. chapter_no 와
(chapter, sort_order) 가 유니크라 재실행하면 내용만 갱신된다.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def quote(value) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def array(values) -> str:
    """text[] 리터럴. 빈 배열과 NULL 을 구분한다."""
    if not values:
        return "NULL"
    inner = ", ".join(quote(v) for v in values)
    return f"ARRAY[{inner}]::text[]"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    chapters = json.loads(Path(args.input).read_text(encoding="utf-8"))

    print("-- 생성물입니다. build_seed.py 로 다시 만들 수 있습니다.")
    print(f"-- 챕터 {len(chapters)} · 용어 {sum(len(c['terms']) for c in chapters)}")
    print("BEGIN;")

    for c in chapters:
        print(f"\n-- {c['no']}. {c['title']}")
        print(
            "INSERT INTO wiki_chapters (chapter_no, title) "
            f"VALUES ({c['no']}, {quote(c['title'])}) "
            "ON CONFLICT (chapter_no) DO UPDATE SET title = EXCLUDED.title, updated_at = now();"
        )

        for i, t in enumerate(c["terms"], start=1):
            print(
                "INSERT INTO wiki_terms "
                "(chapter_id, sort_order, name, name_en, description, usages, examples, meanings) "
                f"SELECT id, {i}, {quote(t['name'])}, {quote(t['en'])}, {quote(t['desc'])}, "
                f"{array(t['usages'])}, {array(t['examples'])}, {array(t['meanings'])} "
                f"FROM wiki_chapters WHERE chapter_no = {c['no']} "
                "ON CONFLICT (chapter_id, sort_order) DO UPDATE SET "
                "name = EXCLUDED.name, name_en = EXCLUDED.name_en, "
                "description = EXCLUDED.description, usages = EXCLUDED.usages, "
                "examples = EXCLUDED.examples, meanings = EXCLUDED.meanings, updated_at = now();"
            )

    # 교재에서 빠진 용어는 남겨두면 유령이 된다. 이번에 넣은 것만 남긴다
    print("\n-- 이번 적재에 없는 용어 정리")
    for c in chapters:
        print(
            "DELETE FROM wiki_terms WHERE chapter_id = "
            f"(SELECT id FROM wiki_chapters WHERE chapter_no = {c['no']}) "
            f"AND sort_order > {len(c['terms'])};"
        )

    print("\nCOMMIT;")
    print(f"\n✅ 챕터 {len(chapters)} · 용어 {sum(len(c['terms']) for c in chapters)}", file=sys.stderr)


if __name__ == "__main__":
    main()
