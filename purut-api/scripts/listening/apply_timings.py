#!/usr/bin/env python3
"""정렬 결과 → 문장 타임스탬프 UPDATE SQL.

사용:
    python3 apply_timings.py --timings timings.json \
        --year 2026 --exam-type SUNEUNG --grade 3 > timings.sql

정렬이 실패한 문장(startMs=null)은 건드리지 않는다 —
0 으로 덮으면 "아직 싱크 전"과 구분이 안 된다.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timings", required=True)
    parser.add_argument("--year", type=int, required=True)
    parser.add_argument("--exam-type", required=True)
    parser.add_argument("--grade", type=int, default=3)
    args = parser.parse_args()

    timings = json.loads(Path(args.timings).read_text())

    print("-- 생성물입니다. apply_timings.py 로 다시 만들 수 있습니다.")
    print("BEGIN;")

    skipped = 0
    for item_no, sentences in sorted(timings.items(), key=lambda kv: int(kv[0])):
        for sentence in sentences:
            if sentence["startMs"] is None or sentence["endMs"] is None:
                skipped += 1
                continue

            start = sentence["startMs"]
            end = max(sentence["endMs"], start)  # 제약(end >= start) 보호

            print(f"""UPDATE listening_sentences s
SET start_ms = {start}, end_ms = {end}
FROM listening_items i JOIN exams e ON e.id = i.exam_id
WHERE s.item_id = i.id AND i.item_no = {int(item_no)} AND s.seq = {sentence['seq']}
  AND e.year = {args.year} AND e.exam_type = '{args.exam_type}' AND e.grade = {args.grade};""")

    print("COMMIT;")
    if skipped:
        print(f"-- 정렬 실패로 건너뛴 문장: {skipped}개")


if __name__ == "__main__":
    main()
