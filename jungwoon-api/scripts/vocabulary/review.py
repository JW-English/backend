#!/usr/bin/env python3
"""생성 결과 검수. 기계적으로 확실한 것만 고친다.

사용:
    python3 review.py                # 보고만
    python3 review.py --fix          # 고쳐서 다시 쓴다

뜻이 맞는지 틀린지는 사람이 봐야 한다. 여기서는 사람이 볼 필요가 없는 것,
즉 같은 문자열인데 표기만 어긋난 것만 손댄다.
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path


def nospace(text: str) -> str:
    return text.replace(" ", "")


def fix_spacing(entry: dict) -> str | None:
    """대표 뜻의 띄어쓰기 복원.

    12자 제한을 맞추려다 "상상할 수 있는" 이 "상상할수있는" 이 된다.
    meanings 쪽에는 제대로 띄어 쓴 같은 말이 있으니 그걸로 되돌린다.
    """
    rep = entry["meaning_ko"]
    for m in entry["meanings"]:
        for alt in m["ko"].split(","):
            alt = alt.strip()
            if nospace(alt) == nospace(rep) and alt.count(" ") > rep.count(" "):
                entry["meaning_ko"] = alt
                return f"{rep} → {alt}"
    return None


def fix_order(entry: dict) -> str | None:
    """대표 뜻에 해당하는 뜻을 맨 앞으로.

    대표 뜻은 가장 흔한 뜻이어야 하는데 meanings 순서가 그와 어긋난 경우가 있다.
    even 은 대표가 '심지어'(ad.) 인데 첫 뜻이 '평평한'(a.) 이었다.
    단어장 화면은 meanings 를 순서대로 보여주므로 첫 줄이 대표 뜻과 달라진다.
    """
    rep = nospace(entry["meaning_ko"])
    for i, m in enumerate(entry["meanings"]):
        if rep in nospace(m["ko"]) or nospace(m["ko"]) in rep:
            if i == 0:
                return None
            before = entry["meanings"][0]
            entry["meanings"].insert(0, entry["meanings"].pop(i))
            return f"{entry['meaning_ko']}: {before['pos']} {before['ko']} → {m['pos']} {m['ko']}"
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="data/generated.jsonl")
    parser.add_argument("--fix", action="store_true")
    args = parser.parse_args()

    path = Path(args.input)
    rows = [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]

    spacing, order = [], []
    for entry in rows:
        note = fix_spacing(entry)
        if note:
            spacing.append((entry["headword"], note))
        note = fix_order(entry)
        if note:
            order.append((entry["headword"], note))

    print(f"■ 대표 뜻 띄어쓰기 {len(spacing)}건")
    for h, n in spacing:
        print(f"   {h:<24} {n}")

    print(f"\n■ 뜻 순서를 대표 뜻에 맞춤 {len(order)}건")
    for h, n in order:
        print(f"   {h:<24} {n}")

    if not args.fix:
        print(f"\n(--fix 를 붙이면 {len(spacing) + len(order)}건을 고쳐 씁니다)")
        return

    shutil.copy(path, path.with_suffix(".jsonl.bak"))
    with path.open("w", encoding="utf-8") as f:
        for entry in rows:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    print(f"\n✅ {len(rows)}행을 다시 썼습니다 (원본은 {path.name}.bak)")


if __name__ == "__main__":
    main()
