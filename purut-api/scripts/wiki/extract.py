#!/usr/bin/env python3
"""영어위키 PDF → 챕터·용어 JSON.

사용:
    python3 extract.py --input ~/Downloads/영어위키.pdf > data/wiki.json

PDF 구조가 규칙적이다. 30챕터, 각 용어가 아래 순서로 이어진다.

    이름
    영문명          (8장 조동사처럼 없는 챕터가 있다)
    설명            (길면 여러 줄로 쪼개져 나온다)
    ex) ...         (선택)
    예문
    <문장>          (선택적으로 여러 줄 — 비교 예문이 있다)
    뜻
    <해석>          (예문 줄 수와 같다)

'예문'/'뜻' 라벨을 기준점으로 잡고 앞뒤를 채운다. 설명이 몇 줄인지 미리 알 수
없어서, 앞의 용어가 끝난 지점부터 '예문'까지를 통째로 머리로 본다.
"""

from __future__ import annotations

import argparse
import json
import re
import sys

import fitz

HEADER = "고등학교 영어 문법 - 쉬운 용어집"
CHAPTER = re.compile(r"^(\d{1,2})\.\s+(.+)$")


def clean(page_text: str) -> list[str]:
    lines = [l.strip() for l in page_text.split("\n") if l.strip()]
    # 머리말과 쪽번호를 걷어낸다
    return [l for l in lines if l != HEADER and not l.isdigit()]


def split_chapters(doc) -> list[dict]:
    """본문에서만 챕터를 찾는다.

    목차 페이지에도 "1. 문장의 기본 구조" 가 있어서 그대로 훑으면 챕터가 두 배로
    잡힌다. 목차는 제목 뒤에 "10개 용어" 가 따라오고 본문은 용어 이름이 온다 —
    첫 챕터가 두 번째로 등장하는 지점부터 본문으로 본다.
    """
    starts = []
    for i in range(doc.page_count):
        for line in clean(doc[i].get_text()):
            m = CHAPTER.match(line)
            if m and int(m.group(1)) == 1:
                starts.append(i)
                break
    body_from = starts[-1] if len(starts) > 1 else 0

    chapters, cur = [], None
    for i in range(body_from, doc.page_count):
        for line in clean(doc[i].get_text()):
            m = CHAPTER.match(line)
            if m and 1 <= int(m.group(1)) <= 30:
                cur = {"no": int(m.group(1)), "title": m.group(2).strip(), "lines": []}
                chapters.append(cur)
            elif cur is not None:
                cur["lines"].append(line)
    return chapters


def parse_terms(lines: list[str]) -> list[dict]:
    """'예문' 라벨 위치를 먼저 모으고, 그 사이를 용어 단위로 자른다."""
    marks = [i for i, l in enumerate(lines) if l == "예문"]

    terms = []
    for idx, ex_at in enumerate(marks):
        # 머리 = 앞 용어가 끝난 다음 줄부터 '예문' 직전까지
        head_start = 0 if idx == 0 else _end_of(lines, marks[idx - 1])
        head = lines[head_start:ex_at]
        if len(head) < 2:
            continue

        # 영문명은 있는 챕터와 없는 챕터가 섞여 있다(8장 조동사에는 없다).
        # 소문자 알파벳으로만 이루어진 짧은 줄이면 영문명으로 본다
        name = head[0]
        if len(head) >= 3 and _looks_english(head[1]):
            en, rest = head[1], head[2:]
        else:
            en, rest = "", head[1:]
        # ex) 로 시작하는 줄은 설명이 아니라 예시 목록이다
        examples = [r for r in rest if r.startswith("ex)")]
        desc = " ".join(r for r in rest if not r.startswith("ex)"))

        # 예문 ~ 뜻 ~ 다음 용어
        try:
            mean_at = lines.index("뜻", ex_at)
        except ValueError:
            continue
        end = _end_of(lines, ex_at)

        terms.append({
            "name": name,
            "en": en,
            "desc": desc,
            "usages": [e[3:].strip() for e in examples],
            "examples": lines[ex_at + 1:mean_at],
            "meanings": lines[mean_at + 1:end],
        })
    return terms


def _looks_english(line: str) -> bool:
    """영문명 줄인가. 설명은 한국어라 한글이 섞인다."""
    return bool(line) and not re.search(r"[가-힣]", line) and len(line) < 60


def _end_of(lines: list[str], ex_at: int) -> int:
    """'예문' 하나가 만드는 블록의 끝. 뜻 뒤로 이어지는 해석 줄까지 포함한다."""
    try:
        mean_at = lines.index("뜻", ex_at)
    except ValueError:
        return len(lines)

    # 예문 줄 수만큼 해석도 이어진다고 보되, 다음 '예문' 을 넘지 않는다
    span = mean_at - ex_at - 1
    end = mean_at + 1 + max(span, 1)
    for i in range(mean_at + 1, len(lines)):
        if lines[i] == "예문":
            return min(end, i - 2)  # 다음 용어의 이름·영문명은 남겨둔다
    return min(end, len(lines))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    doc = fitz.open(args.input)
    chapters = split_chapters(doc)

    out, total = [], 0
    for c in chapters:
        terms = parse_terms(c["lines"])
        total += len(terms)
        out.append({"no": c["no"], "title": c["title"], "terms": terms})

    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
    print(f"\n✅ 챕터 {len(out)} · 용어 {total}", file=sys.stderr)


if __name__ == "__main__":
    main()
