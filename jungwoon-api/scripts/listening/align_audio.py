#!/usr/bin/env python3
"""음원 + 대본 → 문장별 타임스탬프 (강제 정렬).

우리는 공식 스크립트를 갖고 있으므로 '전사'가 아니라 '정렬' 문제다.
Whisper 로 받아쓰면 고유명사·숫자가 틀려 결국 손으로 고쳐야 한다.
텍스트는 대본을 그대로 쓰고 타임스탬프만 정렬에서 가져온다.

사용:
    .venv/bin/python align_audio.py --script script.json --audio-dir <dir> [--item 1]
        > timings.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

# "1.mp3", "16-17.mp3", "16~17.mp3" 모두 받는다.
# 하이픈을 빼먹으면 "16-17.mp3" 가 17번 하나로만 잡혀 16번 음원이 사라진다.
FILE_ITEM_RE = re.compile(r"(\d+)(?:\s*[-~～]\s*(\d+))?\.mp3$")


def normalize(text: str) -> str:
    """정렬기가 다루기 쉬운 형태로. 곡선 따옴표는 ASCII 로 편다."""
    text = unicodedata.normalize("NFKC", text)
    return (
        text.replace("’", "'")
        .replace("‘", "'")
        .replace("“", '"')
        .replace("”", '"')
        .replace("—", " ")
        .strip()
    )


SILENCE_START_RE = re.compile(r"silence_start:\s*([0-9.]+)")
SILENCE_END_RE = re.compile(r"silence_end:\s*([0-9.]+)")

_silence_cache: dict[str, str] = {}


def _silence_log(audio_path: Path) -> str:
    """ffmpeg 무음 탐지 로그. 같은 파일을 여러 번 재지 않도록 캐시한다."""
    key = str(audio_path)
    if key not in _silence_cache:
        import subprocess

        result = subprocess.run(
            ["ffmpeg", "-i", key, "-af", "silencedetect=noise=-35dB:d=0.3", "-f", "null", "-"],
            capture_output=True, text=True,
        )
        _silence_cache[key] = result.stderr
    return _silence_cache[key]


def detect_lead_offset(audio_path: Path, duration: float) -> float:
    """
    한국어 안내가 끝나고 영어 본문이 시작하는 지점(초)을 찾는다.

    수능 듣기 mp3 는 앞에 "대화를 듣고, …" 안내가 붙어 있는데 대본에는 없다.
    이 구간을 남겨두면 정렬기가 <b>맞출 곳이 없는데도 영어 단어를 억지로 끼워 넣는다</b>.
    (실측 14번: 안내가 12.7초까지인데 "Hey, Jake." 가 6.3초에 배치됨)

    보정으로는 못 고친다 — 정렬 결과 자체가 틀리기 때문이다. 그래서 자르고 나서 맞춘다.

    경계는 <b>앞부분에서 마지막으로 나오는 긴 무음</b>이다.

    "가장 긴 무음"으로 잡으면 안 된다 — 안내 문구 자체가 중간에 쉬기 때문이다.
    (2020 6월 8번: 안내 안의 쉼이 2.2초로 안내→본문 경계 1.55초보다 길어
     4.4초를 경계로 잡았고, 실제 영어는 13.1초부터였다)

    안내는 항상 파일 맨 앞에 있고 본문보다 짧다. 실측한 경계는 전부 20% 이내였다
    (14.5% / 17.8% / 19.3%). 30% 안쪽만 보면 본문 중간을 자를 위험이 없다.
    """
    head_limit = duration * 0.3
    candidates: list[float] = []

    log = _silence_log(audio_path)
    starts = SILENCE_START_RE.findall(log)
    ends = SILENCE_END_RE.findall(log)
    for start, end in zip(starts, ends):
        start, end = float(start), float(end)
        # 문장 사이 쉼(0.3~0.9초)과 구분되는 크기여야 한다
        if end <= head_limit and (end - start) >= 1.0:
            candidates.append(end)

    return max(candidates) if candidates else 0.0


MIN_WORDS_PER_SECOND = 1.2


def fix_slow_first_sentence(timings: list[dict], audio_path: Path) -> bool:
    """
    첫 문장이 여전히 안내 구간에 걸쳐 있으면 시작점을 되짚는다.

    안내 구간을 잘라내는 것으로 대부분 해결되지만, 안내가 유난히 긴 문항
    (16~17 처럼 문제를 두 개 읽어주는 경우)에서는 탐색 범위 밖이라 남는다.

    첫 문장의 <b>끝</b>은 정렬이 맞춘 값이라 신뢰할 수 있다. 그래서 끝에서 거꾸로
    "말하는 데 필요한 최소 시간"을 뺀 지점 앞의 마지막 무음을 시작으로 본다.
    """
    first = timings[0] if timings else None
    if not first or first["startMs"] is None or first["endMs"] is None:
        return False

    seconds = (first["endMs"] - first["startMs"]) / 1000
    if seconds <= 0 or first["wordCount"] / seconds >= MIN_WORDS_PER_SECOND:
        return False

    # 단어당 0.25초는 아무리 빨라도 필요하다
    latest_possible = first["endMs"] - max(400, int(first["wordCount"] * 250))

    log = _silence_log(audio_path)
    ends = [float(e) * 1000 for e in SILENCE_END_RE.findall(log)]
    candidates = [e for e in ends if first["startMs"] < e < latest_possible]
    if not candidates:
        return False

    first["correctedFrom"] = first["startMs"]
    first["startMs"] = int(max(candidates))
    return True


def map_audio_files(audio_dir: Path) -> dict[int, Path]:
    mapping: dict[int, Path] = {}
    for path in sorted(audio_dir.glob("*.mp3")):
        match = FILE_ITEM_RE.search(path.name)
        if not match:
            continue
        start = int(match.group(1))
        end = int(match.group(2)) if match.group(2) else start
        for item_no in range(start, end + 1):
            mapping[item_no] = path
    return mapping


def align_item(whisperx, model, metadata, device, audio_path: Path,
               sentences: list[dict]) -> list[dict]:
    """
    문장들을 한 덩어리로 정렬한 뒤, 단어 타임스탬프를 문장에 되돌려 나눈다.

    전체를 한 세그먼트로 넣는 이유: 우리는 각 문장이 어디쯤인지 모른다.
    CTC 정렬기가 오디오 전체에서 단어 위치를 찾아 준다.
    """
    audio = whisperx.load_audio(str(audio_path))
    full_text = " ".join(normalize(s["textEn"]) for s in sentences)

    # 한국어 안내 구간을 잘라내고 맞춘다. 남겨두면 정렬기가 그 안에 영어 단어를 끼워 넣는다
    lead_offset = detect_lead_offset(audio_path, len(audio) / 16000.0)
    audio = audio[int(lead_offset * 16000):]

    duration = len(audio) / 16000.0
    segments = [{"text": full_text, "start": 0.0, "end": duration}]

    result = whisperx.align(segments, model, metadata, audio, device,
                            return_char_alignments=False)

    words = [w for seg in result["segments"] for w in seg.get("words", [])]

    timings: list[dict] = []
    cursor = 0
    for sentence in sentences:
        token_count = len(normalize(sentence["textEn"]).split())
        chunk = words[cursor:cursor + token_count]
        cursor += token_count

        # 정렬기가 일부 단어에 시각을 못 붙이는 경우가 있어 있는 것만 쓴다
        starts = [w["start"] for w in chunk if w.get("start") is not None]
        ends = [w["end"] for w in chunk if w.get("end") is not None]

        # 잘라낸 만큼 되돌려 원본 기준 시각으로 만든다
        offset_ms = int(lead_offset * 1000)
        timings.append({
            "seq": sentence["seq"],
            "startMs": int(min(starts) * 1000) + offset_ms if starts else None,
            "endMs": int(max(ends) * 1000) + offset_ms if ends else None,
            "wordCount": token_count,
            "alignedWords": len(starts),
        })

    return timings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--script", required=True)
    parser.add_argument("--audio-dir", required=True)
    parser.add_argument("--item", type=int, action="append",
                        help="특정 문항만 (여러 번 지정 가능). 없으면 전체")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--out", required=True,
                        help="결과 JSON 경로. 라이브러리가 stdout 을 오염시켜 파일로 쓴다")
    args = parser.parse_args()

    import whisperx  # 무거우므로 인자 검증 후에 임포트한다

    items = json.loads(Path(args.script).read_text())
    if args.item:
        items = [i for i in items if i["itemNo"] in args.item]

    audio = map_audio_files(Path(args.audio_dir))

    print("정렬 모델 로딩…", file=sys.stderr)
    model, metadata = whisperx.load_align_model(language_code="en", device=args.device)

    output: dict[str, list[dict]] = {}
    for item in items:
        path = audio.get(item["itemNo"])
        if path is None:
            print(f"  {item['itemNo']}번: 음원 없음 — 건너뜀", file=sys.stderr)
            continue

        print(f"  {item['itemNo']}번 정렬 중 ({len(item['sentences'])}문장)…", file=sys.stderr)
        timings = align_item(
            whisperx, model, metadata, args.device, path, item["sentences"]
        )
        fix_slow_first_sentence(timings, path)

        # 구간이 뒤집힌 채로 넘어가면 DB 제약에서 터진다
        for row in timings:
            if row["startMs"] is not None and row["endMs"] is not None:
                if row["startMs"] > row["endMs"]:
                    print(f"    ⚠️ {item['itemNo']}번 {row['seq']}번 문장: 시작>끝", file=sys.stderr)

        output[str(item["itemNo"])] = timings

    Path(args.out).write_text(json.dumps(output, ensure_ascii=False, indent=2))
    print(f"저장: {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
