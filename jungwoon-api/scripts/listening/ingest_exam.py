#!/usr/bin/env python3
"""파싱된 대본 + mp3 → 적재용 SQL 생성.

사용:
    python3 ingest_exam.py \
        --script script.json \
        --audio-dir /path/to/mp3 \
        --year 2026 --exam-type SUNEUNG --grade 3 \
        --title "2026학년도 수능 영어" \
        [--translations translations.json] > seed.sql

mp3 는 별도로 스토리지에 올리고, 여기서는 그 키만 SQL 에 박는다.
(서버는 파일 바이트를 경유하지 않는다는 원칙과 같은 이유로 적재도 분리한다)
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

# "1.mp3", "16-17.mp3", "01_문제 01.mp3" → 문항 번호들.
# 하이픈을 빼먹으면 "16-17.mp3" 가 17번 하나로만 잡혀 16번 음원이 사라진다.
FILE_ITEM_RE = re.compile(r"(\d+)(?:\s*[-~～]\s*(\d+))?\.mp3$")


def quote(value) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def duration_ms(path: Path) -> int | None:
    """ffprobe 로 길이를 잰다. 없으면 NULL 로 두고 앱에서 재생하며 채운다."""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=nw=1:nk=1", str(path)],
            capture_output=True, text=True, check=True,
        )
        return int(float(result.stdout.strip()) * 1000)
    except (subprocess.CalledProcessError, FileNotFoundError, ValueError):
        return None


def map_audio_files(audio_dir: Path) -> dict[int, tuple[Path, str]]:
    """
    문항 번호 → (mp3 경로, 스토리지에 올라가 있는 파일명).

    16-17 처럼 묶인 파일은 두 번호에 같은 파일·같은 키를 매핑한다.

    파일명을 새로 만들지 않고 <b>있는 그대로</b> 쓴다. 음원은 이미 스토리지에 올라가 있고,
    스크립트가 임의로 이름을 지어내면 앱이 없는 키를 가리키게 된다.
    (그래서 원본 파일명에 한글·공백이 없어야 한다 — URL 인코딩에서 사고가 난다)
    """
    mapping: dict[int, tuple[Path, str]] = {}
    for path in sorted(audio_dir.glob("*.mp3")):
        match = FILE_ITEM_RE.search(path.name)
        if not match:
            continue
        start = int(match.group(1))
        end = int(match.group(2)) if match.group(2) else start
        if end < start:
            start, end = end, start

        for item_no in range(start, end + 1):
            mapping[item_no] = (path, path.name)
    return mapping


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--script", required=True)
    parser.add_argument("--audio-dir", required=True)
    parser.add_argument("--year", type=int, required=True)
    parser.add_argument("--exam-type", required=True)
    parser.add_argument("--grade", type=int, default=3)
    parser.add_argument("--title", required=True)
    parser.add_argument("--translations", help="{'itemNo-seq': '해석'} 형태 JSON")
    parser.add_argument("--key-prefix", default="listening")
    args = parser.parse_args()

    items = json.loads(Path(args.script).read_text())
    audio = map_audio_files(Path(args.audio_dir))
    translations = json.loads(Path(args.translations).read_text()) if args.translations else {}

    print("-- 생성물입니다. ingest_exam.py 로 다시 만들 수 있습니다.")
    print("BEGIN;")
    print(f"""
INSERT INTO exams (year, exam_type, grade, title, audio_key)
VALUES ({args.year}, {quote(args.exam_type)}, {args.grade}, {quote(args.title)},
        {quote(f"{args.key_prefix}/{args.year}/{args.exam_type.lower()}/intro.mp3")})
ON CONFLICT (year, exam_type, grade) DO UPDATE
    SET title = EXCLUDED.title, audio_key = EXCLUDED.audio_key;
""")

    for item in items:
        item_no = item["itemNo"]
        entry = audio.get(item_no)
        if entry is None:
            print(f"-- ⚠️ {item_no}번 음원을 찾지 못했습니다", flush=True)
            continue

        path, filename = entry
        # 묶음 문항은 같은 파일을 공유하므로 키도 같다
        key = f"{args.key_prefix}/{args.year}/{args.exam_type.lower()}/{filename}"
        ms = duration_ms(path)

        print(f"""
INSERT INTO listening_items (exam_id, item_no, question_text, audio_key, duration_ms)
SELECT e.id, {item_no}, {quote(item['questionText'])}, {quote(key)}, {ms if ms else 'NULL'}
FROM exams e
WHERE e.year = {args.year} AND e.exam_type = {quote(args.exam_type)} AND e.grade = {args.grade}
ON CONFLICT (exam_id, item_no) DO UPDATE
    SET question_text = EXCLUDED.question_text,
        audio_key = EXCLUDED.audio_key,
        duration_ms = EXCLUDED.duration_ms;""")

        # 문장은 통째로 갈아끼운다 — 대본이 바뀌면 seq 가 어긋나기 때문
        print(f"""
DELETE FROM listening_sentences
WHERE item_id = (SELECT i.id FROM listening_items i
                 JOIN exams e ON e.id = i.exam_id
                 WHERE e.year = {args.year} AND e.exam_type = {quote(args.exam_type)}
                   AND e.grade = {args.grade} AND i.item_no = {item_no});""")

        for sentence in item["sentences"]:
            text_ko = translations.get(f"{item_no}-{sentence['seq']}")
            print(f"""INSERT INTO listening_sentences (item_id, seq, speaker, text_en, text_ko, start_ms, end_ms)
SELECT i.id, {sentence['seq']}, {quote(sentence['speaker'])}, {quote(sentence['textEn'])}, {quote(text_ko)}, 0, 0
FROM listening_items i JOIN exams e ON e.id = i.exam_id
WHERE e.year = {args.year} AND e.exam_type = {quote(args.exam_type)}
  AND e.grade = {args.grade} AND i.item_no = {item_no};""")

    print("COMMIT;")


if __name__ == "__main__":
    main()
