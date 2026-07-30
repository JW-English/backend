#!/usr/bin/env python3
"""표제어 → 한글 뜻·예문 생성 (OpenAI).

사용:
    echo 'OPENAI_API_KEY=sk-...' > scripts/vocabulary/.env    # 또는 셸 환경변수
    python3 generate.py --input data/beginner.csv data/intermediate.csv data/advanced.csv
    python3 generate.py --input data/*.csv --only-missing      # 뜻 없는 것만
    python3 generate.py --input data/*.csv --limit 40 --dry-run  # 프롬프트 확인

결과는 data/generated.jsonl 에 한 줄씩 쌓인다. 중간에 끊고 다시 돌리면
이미 있는 표제어는 건너뛴다. 실패한 것만 다시 돌리려면 그냥 재실행하면 된다.

세 세트에 같은 단어가 겹쳐서(4009개 중 1300여 개가 중복) 표제어 단위로 한 번만
생성하고 세트마다 같은 뜻을 쓴다. 레벨별로 뜻을 달리하면 같은 단어가 화면마다
다르게 보인다.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from openai import OpenAI

from normalize import normalize_headword


def load_env() -> None:
    """옆에 있는 .env 에서 키를 읽는다 (git 에 올라가지 않는다).

    셸 환경변수가 이미 있으면 그쪽을 존중한다.
    """
    env = Path(__file__).parent / ".env"
    if not env.exists():
        return
    for line in env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


SYSTEM = """너는 한국 중고등학생용 영어 단어장을 만드는 편집자다.
수능·내신을 준비하는 학생이 읽는다는 것을 전제로 작업한다.

각 표제어마다 다음을 만든다.

1. meanings — 뜻 1~3개. 자주 쓰이는 순서로.
   pos 는 n. v. a. ad. prep. conj. pron. phr. 중 하나.
   ko 는 그 품사에서의 뜻. 유의어는 쉼표로 (예: "진보, 발전").
2. meaning_ko — 대표 뜻 하나. 객관식 선택지에 들어가므로 12자 이내로 짧게.
   meanings 의 첫 번째 뜻과 같은 의미여야 한다.
3. example_en — 예문. 10~18단어. 수능 지문 수준의 문장.
   표제어를 반드시 포함한다(활용형·시제 변화는 괜찮다).
   대표 뜻(meaning_ko)의 의미로 쓴 문장이어야 한다.
4. example_ko — example_en 의 한국어 번역. 직역보다 자연스러운 우리말로.

지켜야 할 것:
- 뜻은 네 표현으로 새로 쓴다. 참고 뜻이 주어져도 그대로 베끼지 말고,
  어떤 의미로 쓰이는 단어인지 고르는 데만 쓴다.
- 표제어에 붙은 [] () ~ 는 변형·생략 표시다. depend on[upon] 은
  depend on 으로, keep A from ~ing 는 자리표시를 실제 말로 채워 예문을 쓴다.
- lie1, lie2 처럼 끝에 숫자가 붙은 것은 동음이의어 구분 표시다.
  숫자는 단어의 일부가 아니다.
- 예문에 고유명사를 쓸 때는 특정 실존 인물을 지목하지 않는다."""

SCHEMA = {
    "name": "vocabulary_entries",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["entries"],
        "properties": {
            "entries": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["headword", "meanings", "meaning_ko",
                                 "example_en", "example_ko"],
                    "properties": {
                        "headword": {
                            "type": "string",
                            "description": "입력으로 준 표제어를 글자 그대로 되돌려준다",
                        },
                        "meanings": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "additionalProperties": False,
                                "required": ["pos", "ko"],
                                "properties": {
                                    "pos": {"type": "string"},
                                    "ko": {"type": "string"},
                                },
                            },
                        },
                        "meaning_ko": {"type": "string"},
                        "example_en": {"type": "string"},
                        "example_ko": {"type": "string"},
                    },
                },
            }
        },
    },
}

HANGUL = re.compile(r"[가-힣]")
# 표제어가 예문에 들어갔는지 볼 때 쓴다. 굴절을 감안해 앞 절반만 맞춰본다
WORDCHARS = re.compile(r"[A-Za-z]+")


def load_words(paths: list[str], only_missing: bool) -> list[dict]:
    """CSV 여러 개 → 표제어 단위 목록. 같은 단어는 한 번만."""
    by_word: dict[str, dict] = {}
    for path in paths:
        for r in csv.DictReader(Path(path).open(encoding="utf-8")):
            word = normalize_headword(r["headword"])
            if not word:
                continue
            entry = by_word.setdefault(word, {"headword": word, "sets": set(), "hint": ""})
            entry["sets"].add(r["set"])
            # xlsx 세트에만 뜻이 들어 있다. 어떤 의미로 쓰는 단어인지 고르는 힌트로만 쓴다
            hint = (r.get("meanings") or r.get("meaning_ko") or "").strip()
            if hint and len(hint) > len(entry["hint"]):
                entry["hint"] = hint

    words = list(by_word.values())
    if only_missing:
        words = [w for w in words if not w["hint"]]
    words.sort(key=lambda w: w["headword"].lower())
    return words


def load_cache(path: Path) -> dict[str, dict]:
    if not path.exists():
        return {}
    cache = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue  # 중간에 끊겨 깨진 줄. 다시 생성된다
        if row.get("headword"):
            cache[row["headword"]] = row
    return cache


def build_prompt(chunk: list[dict]) -> str:
    lines = []
    for w in chunk:
        if w["hint"]:
            lines.append(f'- {w["headword"]}    (참고 뜻: {w["hint"]})')
        else:
            lines.append(f'- {w["headword"]}')
    return (
        f"다음 {len(chunk)}개 표제어를 처리해라. 순서와 개수를 그대로 유지한다.\n\n"
        + "\n".join(lines)
    )


def check(entry: dict, expected: str) -> tuple[str | None, str | None]:
    """(버릴 사유, 눈으로 볼 사유). 버릴 사유가 있으면 저장하지 않는다."""
    meaning_ko = (entry.get("meaning_ko") or "").strip()
    if not meaning_ko:
        return "meaning_ko 비어 있음", None
    if not HANGUL.search(meaning_ko):
        return f"meaning_ko 에 한글이 없음: {meaning_ko!r}", None
    if len(meaning_ko) > 30:
        return f"meaning_ko 가 너무 김({len(meaning_ko)}자)", None

    if not entry.get("meanings"):
        return "meanings 비어 있음", None
    for m in entry["meanings"]:
        if not (m.get("ko") or "").strip():
            return "meanings 안에 빈 뜻이 있음", None

    example_en = (entry.get("example_en") or "").strip()
    example_ko = (entry.get("example_ko") or "").strip()
    if not example_en or not example_ko:
        return "예문 비어 있음", None
    if not HANGUL.search(example_ko):
        return "example_ko 에 한글이 없음", None
    if HANGUL.search(example_en):
        return "example_en 에 한글이 섞임", None

    # 표제어가 예문에 나오는지 본다. 형태소 분석기가 없어 어간 일치로 때우는데
    # kept/ran/lying 같은 불규칙 활용을 놓친다. 버리면 그 단어가 영영 통과하지
    # 못하고 재시도만 돌므로, 저장은 하고 사람이 볼 목록에만 남긴다.
    #
    # 숙어는 토큰 아무거나 하나만 걸리면 넘어간다. "keep A from -ing" 는
    # "kept us from going" 처럼 첫 단어가 변형되고 사이에 목적어가 낀다
    lowered = example_en.lower()
    tokens = [t.lower() for t in WORDCHARS.findall(expected)
              if len(t) >= 3 and t.lower() != "ing"]
    if tokens and not any(t[: max(4, len(t) - 2)] in lowered for t in tokens):
        return None, f"예문에 표제어가 안 보임 — {example_en!r}"
    return None, None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", nargs="+", required=True)
    parser.add_argument("--out", default="data/generated.jsonl")
    parser.add_argument("--model", default="gpt-4.1")
    parser.add_argument("--chunk", type=int, default=20, help="한 요청에 담을 단어 수")
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--limit", type=int, help="앞에서 N개만 (시험용)")
    parser.add_argument("--sample", type=int,
                        help="전체에서 고르게 N개만 (품질 점검용)")
    parser.add_argument("--words", nargs="+",
                        help="지정한 표제어만 (까다로운 것 확인용)")
    parser.add_argument("--only-missing", action="store_true",
                        help="참고 뜻이 없는 단어만 (PDF 세트)")
    parser.add_argument("--regenerate", action="store_true",
                        help="이미 만든 것도 다시 생성")
    parser.add_argument("--dry-run", action="store_true", help="프롬프트만 출력")
    args = parser.parse_args()

    load_env()

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    words = load_words(args.input, args.only_missing)
    cache = {} if args.regenerate else load_cache(out_path)

    todo = [w for w in words if w["headword"] not in cache]
    if args.words:
        wanted = {normalize_headword(w) for w in args.words}
        todo = [w for w in todo if w["headword"] in wanted]
    if args.sample:
        # 알파벳순이라 앞에서 자르면 a 로 시작하는 단어만 나온다.
        # 전 구간에서 고르게 뽑아야 세트·난이도가 섞인다
        step = max(1, len(todo) // args.sample)
        todo = todo[::step][: args.sample]
    if args.limit:
        todo = todo[: args.limit]

    print(f"표제어 {len(words)} · 완료 {len(words) - len([w for w in words if w['headword'] not in cache])}"
          f" · 남음 {len(todo)}", file=sys.stderr)
    if not todo:
        print("생성할 것이 없습니다.", file=sys.stderr)
        return

    chunks = [todo[i:i + args.chunk] for i in range(0, len(todo), args.chunk)]

    if args.dry_run:
        print(SYSTEM)
        print("\n" + "=" * 60 + "\n")
        print(build_prompt(chunks[0]))
        print(f"\n(청크 {len(chunks)}개 · 모델 {args.model})", file=sys.stderr)
        return

    if not os.environ.get("OPENAI_API_KEY"):
        print("OPENAI_API_KEY 가 설정되지 않았습니다.", file=sys.stderr)
        raise SystemExit(1)

    client = OpenAI()
    lock = threading.Lock()
    out_file = out_path.open("a", encoding="utf-8")
    stats = {"ok": 0, "rejected": 0, "failed_chunks": 0, "in": 0, "out": 0}
    rejects: list[str] = []
    flagged: list[str] = []

    def run_chunk(chunk: list[dict]) -> None:
        last_error = None
        for attempt in range(4):
            try:
                res = client.chat.completions.create(
                    model=args.model,
                    messages=[
                        {"role": "system", "content": SYSTEM},
                        {"role": "user", "content": build_prompt(chunk)},
                    ],
                    response_format={"type": "json_schema", "json_schema": SCHEMA},
                    temperature=0.4,
                )
                entries = json.loads(res.choices[0].message.content)["entries"]
                # 표기를 다듬어 돌려주는 경우가 있어(괄호 제거 등) 정규화해서 맞춘다.
                # 그래도 안 맞고 개수가 같으면 순서대로 짝짓는다
                by_head = {normalize_headword(e.get("headword") or ""): e for e in entries}
                aligned = len(entries) == len(chunk)

                good, bad = [], []
                for i, w in enumerate(chunk):
                    entry = by_head.get(w["headword"])
                    if entry is None and aligned:
                        entry = entries[i]
                    if entry is None:
                        bad.append(f'{w["headword"]}: 응답에 없음')
                        continue
                    fatal, warn = check(entry, w["headword"])
                    if fatal:
                        bad.append(f'{w["headword"]}: {fatal}')
                        continue
                    entry["headword"] = w["headword"]  # 캐시 키를 우리 표기로 고정
                    good.append(entry)
                    if warn:
                        flagged.append(f'{w["headword"]}: {warn}')

                with lock:
                    for entry in good:
                        out_file.write(json.dumps(entry, ensure_ascii=False) + "\n")
                    out_file.flush()
                    stats["ok"] += len(good)
                    stats["rejected"] += len(bad)
                    rejects.extend(bad)
                    if res.usage:
                        stats["in"] += res.usage.prompt_tokens
                        stats["out"] += res.usage.completion_tokens
                    done = stats["ok"] + stats["rejected"]
                    print(f"\r  {done}/{len(todo)}  (통과 {stats['ok']} · 반려 {stats['rejected']})",
                          end="", file=sys.stderr, flush=True)
                return
            except Exception as exc:  # noqa: BLE001 — 무엇이든 재시도한다
                last_error = exc
                time.sleep(2 ** attempt)

        with lock:
            stats["failed_chunks"] += 1
            print(f"\n⚠️  청크 실패 ({chunk[0]['headword']}…): {last_error}", file=sys.stderr)

    print(f"청크 {len(chunks)}개 · 모델 {args.model} · 동시 {args.workers}", file=sys.stderr)
    started = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(run_chunk, c) for c in chunks]
        for f in as_completed(futures):
            f.result()
    out_file.close()

    elapsed = int(time.time() - started)
    print(f"\n\n✅ 통과 {stats['ok']} · 반려 {stats['rejected']} · 실패한 청크 {stats['failed_chunks']}"
          f" · {elapsed // 60}분 {elapsed % 60}초", file=sys.stderr)
    print(f"   토큰 입력 {stats['in']:,} · 출력 {stats['out']:,}", file=sys.stderr)

    if rejects:
        print(f"\n반려 {len(rejects)}건 (재실행하면 다시 시도합니다):", file=sys.stderr)
        for r in rejects[:20]:
            print(f"     {r}", file=sys.stderr)
        if len(rejects) > 20:
            print(f"     … 외 {len(rejects) - 20}건", file=sys.stderr)

    if flagged:
        print(f"\n눈으로 볼 것 {len(flagged)}건 (저장은 했습니다):", file=sys.stderr)
        for f in flagged[:20]:
            print(f"     {f}", file=sys.stderr)
        if len(flagged) > 20:
            print(f"     … 외 {len(flagged) - 20}건", file=sys.stderr)

    remaining = len(todo) - stats["ok"]
    if remaining > 0:
        print(f"\n{remaining}개가 아직 남았습니다. 같은 명령을 다시 실행하세요.", file=sys.stderr)


if __name__ == "__main__":
    main()
