#!/usr/bin/env python3
"""의미쓰기 워크시트 PDF → 중간 CSV (영단어만).

사용:
    python3 extract_pdf.py --input beginner.pdf --set beginner > data/beginner.csv

워크시트는 빈칸 채우기용이라 한글 뜻이 없다. meaning_ko / meanings 를 비워 두고
뒤 단계에서 채운 뒤 build_seed.py 로 넘긴다.

한 쪽이 한 Day 이고 "1. word    21. word" 처럼 2단 배치다.
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--set", required=True, help="beginner | intermediate | advanced")
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

        # (번호, 단어) 를 모아 번호순으로 정렬한다.
        # 2단 배치라 읽는 순서가 1,21,2,22... 로 섞여 나온다
        entries: list[tuple[int, str]] = []
        for line in text.split("\n"):
            for mm in ENTRY_RE.finditer(line):
                entries.append((int(mm.group(1)), mm.group(2).strip()))

        entries.sort(key=lambda e: e[0])
        seen = set()
        for no, word in entries:
            if no in seen:
                continue
            seen.add(no)
            writer.writerow([args.set, day_no, "", no, word, "", "", ""])
            total += 1

        if len(seen) < 40:
            warnings.append(f"Day {day_no}: {len(seen)}개만 추출 (보통 40개)")

    print(f"✅ {total}단어 · {doc.page_count}쪽", file=sys.stderr)
    for w in warnings:
        print(f"⚠️  {w}", file=sys.stderr)


if __name__ == "__main__":
    main()
