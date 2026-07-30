#!/usr/bin/env python3
"""의미쓰기 워크시트 PDF → 중간 CSV (영단어만).

사용:
    python3 extract_pdf.py --input beginner.pdf --set beginner > data/beginner.csv

한글 뜻이 없는 자료를 다룬다. meaning_ko / meanings 를 비워 두고
뒤 단계(generate.py)에서 채운 뒤 build_seed.py 로 넘긴다.

레이아웃 두 가지를 모두 읽는다:
  · 의미쓰기 워크시트  "1. provide    21. environment"
  · 표 형식            NO | Spelling | Meaning
둘 다 한 쪽이 한 Day 이고 2단 배치라 읽는 순서가 1,21,2,22... 로 섞인다.
숙어(run into, get along with)가 섞여 있어 공백을 단어 경계로 보면 잘린다.
"""

from __future__ import annotations

import argparse
import csv
import re
import sys

import fitz

DAY_RE = re.compile(r"Day\s*0*(\d+)", re.IGNORECASE)
# "12. get along with" — 번호 뒤부터, 다음 번호나 줄 끝 전까지.
# 공백 2칸 이상을 열 구분으로 본다 (숙어 안의 한 칸 공백은 살린다).
#
# 표제어에 이런 것들이 섞여 있어 글자만 허용하면 통째로 빠진다:
#   depend on[upon]           대괄호
#   have difficulty (in) -ing 괄호 + 하이픈 시작 토큰
#   lie1 / lie2               동음이의어 숫자 첨자
#   keep A from ?ing          물결·물음표 자리표시
#   take ~ into account
#   (the) chances are (that)  괄호로 시작
_CH = r"[\w'\-\[\]()?~]"
ENTRY_RE = re.compile(
    r"(\d{1,2})\.\s+"
    r"([A-Za-z(\[]" + _CH + r"*(?:\s+" + _CH + r"+)*?)"
    r"(?=\s{2,}|\s*$)"
)


def parse_worksheet(text: str) -> list[tuple[int, str]]:
    """'1. provide    21. environment' 형식 (의미쓰기 워크시트)."""
    entries: list[tuple[int, str]] = []
    for line in text.split("\n"):
        for m in ENTRY_RE.finditer(line):
            entries.append((int(m.group(1)), m.group(2).strip()))
    return entries


def parse_table(text: str) -> list[tuple[int, str]]:
    """NO | Spelling | Meaning 표 형식.

    추출하면 헤더 뒤로 번호와 단어가 번갈아 나온다:
        1, provide, 21, environment, 2, develop, 22, expense, ...
    """
    lines = [l.strip() for l in text.split("\n") if l.strip()]

    header = [i for i, l in enumerate(lines[:12]) if l.lower() == "meaning"]
    if not header:
        return []

    tokens = lines[max(header) + 1:]
    entries: list[tuple[int, str]] = []
    i = 0
    while i < len(tokens) - 1:
        if tokens[i].isdigit():
            entries.append((int(tokens[i]), tokens[i + 1]))
            i += 2
        else:
            i += 1
    return entries


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--set", required=True, help="beginner | intermediate | advanced")
    parser.add_argument("--expect", type=int, default=40, help="Day 당 단어 수 (경고 기준)")
    args = parser.parse_args()

    doc = fitz.open(args.input)

    writer = csv.writer(sys.stdout)
    writer.writerow(
        ["set", "day_no", "day_title", "sort_order", "headword", "word_type",
         "meaning_ko", "meanings"]
    )

    total = 0
    warnings = []

    for page_no in range(doc.page_count):
        text = doc[page_no].get_text()

        m = DAY_RE.search(text)
        if not m:
            warnings.append(f"{page_no + 1}쪽: Day 를 찾지 못해 건너뜁니다")
            continue
        day_no = int(m.group(1))

        # 레이아웃이 두 가지다. 표 헤더가 보이면 표, 아니면 워크시트로 읽는다
        entries = parse_table(text) or parse_worksheet(text)

        # 2단 배치라 읽는 순서가 1,21,2,22... 로 섞여 나온다
        entries.sort(key=lambda e: e[0])
        seen = set()
        for no, word in entries:
            if no in seen:
                continue
            seen.add(no)
            writer.writerow([args.set, day_no, "", no, word, "", "", ""])
            total += 1

        if len(seen) < args.expect:
            warnings.append(f"Day {day_no}: {len(seen)}개만 추출 (기대 {args.expect}개)")

    print(f"✅ {total}단어 · {doc.page_count}쪽", file=sys.stderr)
    for w in warnings:
        print(f"⚠️  {w}", file=sys.stderr)


if __name__ == "__main__":
    main()
