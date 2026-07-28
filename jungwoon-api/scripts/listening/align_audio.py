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

FILE_ITEM_RE = re.compile(r"(\d+)(?:\s*[~～]\s*(\d+))?\.mp3$")


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


SILENCE_END_RE = re.compile(r"silence_end:\s*([0-9.]+)")


def silence_ends(audio_path: Path) -> list[float]:
    """무음이 끝나는 지점들(초). 한국어 안내와 영어 본문의 경계를 찾는 데 쓴다."""
    import subprocess

    result = subprocess.run(
        ["ffmpeg", "-i", str(audio_path), "-af", "silencedetect=noise=-35dB:d=0.6", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    return [float(m) for m in SILENCE_END_RE.findall(result.stderr)]


def fix_leading_offset(timings: list[dict], audio_path: Path) -> None:
    """
    첫 문장의 시작을 보정한다.

    수능 듣기 mp3 는 앞에 한국어 안내("다음을 듣고, …")가 붙어 있는데 대본에는 없다.
    정렬기는 맞출 곳이 없으니 첫 단어를 안내 구간에 끼워 넣는다.
    (실측: "Hello, viewers." 가 2.3초로 잡혔지만 실제 영어는 9.7초부터 시작)

    안내와 본문 사이에는 긴 무음이 있으므로, 첫 문장이 끝나기 전에 끝나는 무음 중
    가장 늦은 지점을 시작으로 본다.

    상한은 <b>두 번째 문장의 시작이 아니라 첫 문장 자신의 끝</b>이다.
    두 번째 문장 기준으로 잡으면 첫 문장의 끝을 넘어서는 지점이 뽑혀
    시작 > 끝인 구간이 만들어진다 (12·15번에서 실제로 발생).

    또 문장을 읽는 데 걸리는 최소 시간을 남긴다. 무음이 문장 끝에 바짝 붙어 있으면
    말할 시간이 없는 0초짜리 구간이 되기 때문이다.
    """
    first = timings[0]
    if first["startMs"] is None or first["endMs"] is None:
        return

    # 단어당 0.2초는 아무리 빨라도 필요하다
    min_duration_ms = max(300, first["wordCount"] * 200)
    upper_bound = first["endMs"] - min_duration_ms

    candidates = [end for end in silence_ends(audio_path) if end * 1000 < upper_bound]
    if not candidates:
        return

    corrected = int(max(candidates) * 1000)
    if corrected > first["startMs"]:
        first["correctedFrom"] = first["startMs"]
        first["startMs"] = corrected


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

        timings.append({
            "seq": sentence["seq"],
            "startMs": int(min(starts) * 1000) if starts else None,
            "endMs": int(max(ends) * 1000) if ends else None,
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
        fix_leading_offset(timings, path)

        # 보정이 구간을 뒤집지 않았는지 확인한다. 뒤집힌 채로 넘어가면 DB 제약에서 터진다
        for row in timings:
            if row["startMs"] is not None and row["endMs"] is not None:
                if row["startMs"] > row["endMs"]:
                    print(f"    ⚠️ {item['itemNo']}번 {row['seq']}번 문장: 시작>끝 — 보정 취소",
                          file=sys.stderr)
                    row["startMs"] = row.pop("correctedFrom", row["startMs"])

        output[str(item["itemNo"])] = timings

    Path(args.out).write_text(json.dumps(output, ensure_ascii=False, indent=2))
    print(f"저장: {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
