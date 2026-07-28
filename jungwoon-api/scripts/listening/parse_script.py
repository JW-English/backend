#!/usr/bin/env python3
"""수능 듣기 대본 PDF → 문항·문장 구조 JSON.

사용:
    python3 parse_script.py 스크립트.pdf > script.json

텍스트 추출은 PyMuPDF 를 쓴다. pdftotext 는 어느 모드로도 깨진다:
  - 기본/-layout : 커닝 때문에 단어 중간에 공백이 들어간다 ("Jour ney", "f ive")
  - -raw         : 단어 사이 공백이 사라진다 ("lookingforabutterknifeset")
둘 다 정렬(alignment) 단계에서 단어 수를 어긋나게 만들어 타임스탬프를 밀어버린다.
"""

from __future__ import annotations

import json
import re

import sys

# 문항 시작: "1. 다음을 듣고, ..."
ITEM_RE = re.compile(r"^(\d+)\.\s+(.*)$")
# 16~17 처럼 한 음원을 공유하는 묶음.
#
# 물결표가 회차마다 다르다 — 실측: 16개 회차는 ～(U+FF5E), 2021 수능만 ∼(U+223C).
# 이 줄을 못 알아보면 16·17번 대본이 15번에 붙고 16·17번은 빈 채로 적재된다.
TILDES = "~∼〜～"
GROUP_RE = re.compile(rf"^\[(\d+)\s*[{TILDES}]\s*(\d+)\]\s*(.*)$")
# 화자: "M:", "W:", "M1:" 등
SPEAKER_RE = re.compile(r"^(M\d?|W\d?)\s*:\s*(.*)$")

# 페이지 꼬리말. 줄 끝에 그대로 붙어 나오기도 하고(16번 질문),
# 한 줄을 통째로 차지하기도 해서 "어디에 있든 제거" 방식으로 처리한다
COPYRIGHT_RE = re.compile(r"이 문제지에 관한 저작권은[^\n]*?있습니다\.?")

# 페이지 머리말·쪽번호·안내 문구
NOISE_RE = re.compile(
    r"^(-\s*\d+\s*-|\d+학년도.*|영어 영역.*|.*번부터.*들려줍니다\.?)$"
)

# 문장 분리: 마침표·물음표·느낌표 뒤 공백. 약어(Mr. 등) 뒤에서는 자르지 않는다
ABBREVIATIONS = {"Mr", "Mrs", "Ms", "Dr", "St", "Prof", "vs", "etc", "Jr", "Sr"}
SENTENCE_END_RE = re.compile(r'(?<=[.!?])["”’\']?\s+')


def extract_text(pdf_path: str) -> str:
    import fitz  # PyMuPDF

    with fitz.open(pdf_path) as doc:
        return "".join(page.get_text() for page in doc)


def split_sentences(text: str) -> list[str]:
    """화자 한 턴을 문장 단위로 자른다."""
    parts = SENTENCE_END_RE.split(text)

    merged: list[str] = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        # 앞 조각이 약어로 끝났으면 잘못 잘린 것이므로 다시 붙인다
        if merged:
            tail = merged[-1].rstrip(".").split()[-1] if merged[-1].rstrip(".").split() else ""
            if tail in ABBREVIATIONS:
                merged[-1] = f"{merged[-1]} {part}"
                continue
        merged.append(part)
    return merged


def parse(text: str) -> list[dict]:
    items: list[dict] = []
    current: dict | None = None
    speaker: str | None = None
    buffer: list[str] = []
    # 16~17 처럼 한 음원을 공유하는 문항 번호들
    pending_group: list[int] = []

    def flush_turn() -> None:
        """모아둔 화자 발화를 문장으로 쪼개 현재 문항에 넣는다."""
        nonlocal speaker, buffer
        if current is None or speaker is None or not buffer:
            speaker, buffer = None, []
            return
        turn = " ".join(buffer).strip()
        for sentence in split_sentences(turn):
            current["sentences"].append({"speaker": speaker, "textEn": sentence})
        speaker, buffer = None, []

    def start_item(item_no: int, question: str, group: list[int] | None = None) -> None:
        nonlocal current
        flush_turn()
        current = {
            "itemNo": item_no,
            "questionText": question.strip(),
            "sharedWith": group or [],
            "sentences": [],
        }
        items.append(current)

    for line in text.splitlines():
        line = COPYRIGHT_RE.sub("", line).strip()
        if not line or NOISE_RE.match(line):
            continue

        group_match = GROUP_RE.match(line)
        if group_match:
            start, end = int(group_match.group(1)), int(group_match.group(2))
            pending_group = list(range(start, end + 1))
            start_item(start, group_match.group(3), pending_group)
            continue

        item_match = ITEM_RE.match(line)
        if item_match:
            item_no = int(item_match.group(1))
            question = item_match.group(2)
            # 묶음 문항의 개별 질문(16., 17.)은 새 문항이 아니라 질문 텍스트만 채운다
            if item_no in pending_group:
                for item in items:
                    if item["itemNo"] == item_no:
                        item["questionText"] = question.strip()
                        break
                else:
                    base = next(i for i in items if i["itemNo"] == pending_group[0])
                    items.append({
                        "itemNo": item_no,
                        "questionText": question.strip(),
                        "sharedWith": pending_group,
                        # 같은 음원을 공유하므로 문장도 같다
                        "sentences": [dict(s) for s in base["sentences"]],
                    })
                continue
            start_item(item_no, question)
            pending_group = []
            continue

        speaker_match = SPEAKER_RE.match(line)
        if speaker_match:
            flush_turn()
            speaker = speaker_match.group(1)
            buffer = [speaker_match.group(2)]
            continue

        # 이어지는 줄 (raw 모드는 줄바꿈만 있고 들여쓰기가 없다)
        if speaker is not None:
            buffer.append(line)

    flush_turn()

    # 묶음 문항은 본문을 나중에 읽으므로, 뒤늦게 채워진 문장을 복사해 맞춘다
    for item in items:
        if item["sharedWith"] and not item["sentences"]:
            base = next(
                (i for i in items if i["itemNo"] == item["sharedWith"][0] and i["sentences"]),
                None,
            )
            if base:
                item["sentences"] = [dict(s) for s in base["sentences"]]

    for item in items:
        for index, sentence in enumerate(item["sentences"]):
            sentence["seq"] = index

    items.sort(key=lambda i: i["itemNo"])
    return items


def main() -> None:
    if len(sys.argv) < 2:
        print("사용: parse_script.py <스크립트.pdf>", file=sys.stderr)
        raise SystemExit(1)

    items = parse(extract_text(sys.argv[1]))
    json.dump(items, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
