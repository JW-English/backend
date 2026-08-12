#!/usr/bin/env python3
"""리스닝 회차 일괄 적재.

R2 에 올라간 회차를 찾아 순서대로 처리한다.

    R2에서 내려받기 → PDF 파싱 → 강제 정렬 → 적재 → 검증

사용:
    python3 run_pipeline.py --all
    python3 run_pipeline.py --year 2026
    python3 run_pipeline.py --year 2026 --type suneung --force

이미 적재된 회차는 건너뛴다. 정렬이 회차당 몇 분 걸리므로 중단되더라도
받아둔 음원과 정렬 결과를 재사용해 이어서 돌린다.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
API_ROOT = HERE.parent.parent          # purut-api/
WORK_ROOT = HERE / "work"              # 내려받은 원본과 중간 산출물 (gitignore)
VENV_PYTHON = HERE / ".venv" / "bin" / "python"

# 폴더명 → (DB exam_type, 화면에 보일 이름)
EXAM_TYPES = {
    "suneung": ("SUNEUNG", "수능"),
    "mock_9": ("MOCK_9", "9월 모의평가"),
    "mock_6": ("MOCK_6", "6월 모의평가"),
}

# 학생용 문항 음원만 고른다. intro.mp3 와 폴더 더미 객체는 제외된다
AUDIO_KEY_RE = re.compile(r"/(\d+(?:[-~～]\d+)?)\.mp3$")

DEFAULT_PSQL = "docker exec -i jungwoon-postgres psql -U jungwoon -d jungwoon -q"


@dataclass
class Exam:
    year: int
    folder: str            # suneung | mock_6 | mock_9

    @property
    def exam_type(self) -> str:
        return EXAM_TYPES[self.folder][0]

    @property
    def title(self) -> str:
        return f"{self.year}학년도 {EXAM_TYPES[self.folder][1]} 영어 듣기"

    @property
    def slug(self) -> str:
        return f"{self.year}-{self.folder}"

    @property
    def prefix(self) -> str:
        return f"listening/{self.year}/{self.folder}"

    @property
    def workdir(self) -> Path:
        return WORK_ROOT / self.slug


def load_env() -> dict[str, str]:
    """purut-api/.env 를 읽는다. 비밀 값은 로그에 남기지 않는다."""
    env_path = API_ROOT / ".env"
    if not env_path.exists():
        sys.exit(f"{env_path} 가 없습니다. STORAGE_* 값을 채워주세요.")

    env: dict[str, str] = {}
    for line in env_path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()

    missing = [k for k in ("STORAGE_ENDPOINT", "STORAGE_BUCKET",
                           "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY") if not env.get(k)]
    if missing:
        sys.exit(f".env 에 다음 값이 비어 있습니다: {', '.join(missing)}")
    return env


def aws(env: dict[str, str], *args: str) -> str:
    """자격증명은 환경변수로만 넘긴다 (명령행에 남기지 않는다)."""
    process_env = {
        **os.environ,
        "AWS_ACCESS_KEY_ID": env["STORAGE_ACCESS_KEY"],
        "AWS_SECRET_ACCESS_KEY": env["STORAGE_SECRET_KEY"],
        "AWS_DEFAULT_REGION": env.get("STORAGE_REGION", "auto"),
    }
    result = subprocess.run(
        ["aws", *args, "--endpoint-url", env["STORAGE_ENDPOINT"]],
        capture_output=True, text=True, env=process_env,
    )
    if result.returncode != 0:
        raise RuntimeError(f"aws 실패: {result.stderr.strip()[:200]}")
    return result.stdout


def discover_exams(env: dict[str, str]) -> list[Exam]:
    keys = aws(env, "s3api", "list-objects-v2",
               "--bucket", env["STORAGE_BUCKET"], "--prefix", "listening/",
               "--query", "Contents[].Key", "--output", "text").split()

    found: set[tuple[int, str]] = set()
    for key in keys:
        parts = key.split("/")
        if len(parts) >= 4 and parts[0] == "listening" and parts[2] in EXAM_TYPES:
            if parts[1].isdigit():
                found.add((int(parts[1]), parts[2]))

    return sorted((Exam(year, folder) for year, folder in found),
                  key=lambda e: (-e.year, e.folder))


def download(env: dict[str, str], exam: Exam) -> tuple[Path, Path | None]:
    """음원과 대본을 내려받는다. 이미 받아둔 파일은 건너뛴다 (aws s3 sync)."""
    audio_dir = exam.workdir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)

    aws(env, "s3", "sync", f"s3://{env['STORAGE_BUCKET']}/{exam.prefix}/", str(audio_dir),
        "--exclude", "*", "--include", "*.mp3", "--include", "*.pdf")

    # intro.mp3 는 문항이 아니라 안내 음원이라 정렬 대상에서 뺀다
    intro = audio_dir / "intro.mp3"
    if intro.exists():
        intro.unlink()

    pdfs = list(audio_dir.glob("*.pdf"))
    if pdfs:
        # 대본은 음원 폴더 밖으로 옮긴다 (glob('*.mp3') 와 섞이지 않게)
        pdf = exam.workdir / "script.pdf"
        pdfs[0].replace(pdf)
        for extra in pdfs[1:]:
            extra.unlink()
        return audio_dir, pdf

    existing = exam.workdir / "script.pdf"
    return audio_dir, existing if existing.exists() else None


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, capture_output=True, text=True, **kwargs)


def already_ingested(psql: str, exam: Exam) -> bool:
    query = (
        "select count(s.id) from listening_sentences s "
        "join listening_items i on i.id = s.item_id "
        "join exams e on e.id = i.exam_id "
        f"where e.year = {exam.year} and e.exam_type = '{exam.exam_type}'"
    )
    result = run([*psql.split(), "-tAc", query])
    return result.returncode == 0 and result.stdout.strip().isdigit() \
        and int(result.stdout.strip()) > 0


def apply_sql(psql: str, sql_path: Path) -> None:
    with sql_path.open() as f:
        result = subprocess.run(psql.split(), stdin=f, capture_output=True, text=True)
    errors = [l for l in result.stderr.splitlines() if "ERROR" in l]
    if errors:
        raise RuntimeError(f"SQL 적용 실패: {errors[0][:200]}")


def validate(script_path: Path, timings_path: Path) -> list[str]:
    """사람이 24개를 다 볼 수 없으므로 이상 신호만 뽑는다."""
    script = json.loads(script_path.read_text())
    timings = json.loads(timings_path.read_text())

    warnings: list[str] = []

    expected_items = {item["itemNo"] for item in script}
    aligned_items = {int(no) for no in timings}
    missing = expected_items - aligned_items
    if missing:
        warnings.append(f"정렬 안 된 문항: {sorted(missing)}")

    total = aligned = 0
    for no, rows in timings.items():
        for row in rows:
            total += row["wordCount"]
            aligned += row["alignedWords"]
            if row["startMs"] is not None and row["endMs"] is not None:
                if row["startMs"] > row["endMs"]:
                    warnings.append(f"{no}번 {row['seq']}번 문장 구간 역전")

    if total and aligned / total < 0.95:
        warnings.append(f"정렬률 낮음: {aligned / total * 100:.1f}%")

    # 대본이 비어 있는 문항. 파싱이 그 회차에서 실패했다는 신호다
    empty = sorted((int(no) for no, rows in timings.items() if not rows))
    if empty:
        warnings.append(f"대본이 비어 있는 문항: {empty}")

    # 첫 문장이 한국어 안내 구간에 걸리면 발화 속도가 비정상적으로 낮게 나온다
    for no, rows in timings.items():
        if not rows:
            continue
        first = rows[0]
        if first["startMs"] is None or first["endMs"] is None:
            continue
        seconds = (first["endMs"] - first["startMs"]) / 1000
        if seconds > 0 and first["wordCount"] / seconds < 1.2:
            warnings.append(f"{no}번 첫 문장 속도 이상 ({first['wordCount'] / seconds:.2f} 단어/초)")

    return warnings


def process(env: dict[str, str], exam: Exam, psql: str, force: bool) -> tuple[str, list[str]]:
    if not force and already_ingested(psql, exam):
        return "건너뜀 (이미 적재됨)", []

    exam.workdir.mkdir(parents=True, exist_ok=True)
    audio_dir, pdf = download(env, exam)
    if pdf is None:
        return "실패: 대본 PDF 없음", []

    script_path = exam.workdir / "script.json"
    timings_path = exam.workdir / "timings.json"

    if not script_path.exists():
        result = run([sys.executable, str(HERE / "parse_script.py"), str(pdf)])
        if result.returncode != 0:
            return f"실패: 파싱 — {result.stderr.strip()[:120]}", []
        script_path.write_text(result.stdout)

    # 정렬이 가장 오래 걸린다. 한 번 만든 결과는 재사용한다
    if not timings_path.exists():
        result = run([str(VENV_PYTHON), str(HERE / "align_audio.py"),
                      "--script", str(script_path), "--audio-dir", str(audio_dir),
                      "--out", str(timings_path)])
        if result.returncode != 0 or not timings_path.exists():
            return f"실패: 정렬 — {result.stderr.strip()[-160:]}", []

    # 해석 파일이 있으면 함께 넣는다 (없으면 영어만 적재)
    translations = HERE / f"translations-{exam.year}-{exam.folder}.json"
    ingest_args = [sys.executable, str(HERE / "ingest_exam.py"),
                   "--script", str(script_path), "--audio-dir", str(audio_dir),
                   "--year", str(exam.year), "--exam-type", exam.exam_type,
                   "--grade", "3", "--title", exam.title]
    if translations.exists():
        ingest_args += ["--translations", str(translations)]

    result = run(ingest_args)
    if result.returncode != 0:
        return f"실패: SQL 생성 — {result.stderr.strip()[:120]}", []
    seed_sql = exam.workdir / "seed.sql"
    seed_sql.write_text(result.stdout)

    result = run([sys.executable, str(HERE / "apply_timings.py"),
                  "--timings", str(timings_path), "--year", str(exam.year),
                  "--exam-type", exam.exam_type, "--grade", "3"])
    if result.returncode != 0:
        return f"실패: 타임스탬프 SQL — {result.stderr.strip()[:120]}", []
    timing_sql = exam.workdir / "timings.sql"
    timing_sql.write_text(result.stdout)

    try:
        apply_sql(psql, seed_sql)
        apply_sql(psql, timing_sql)
    except RuntimeError as e:
        return f"실패: {e}", []

    return "완료", validate(script_path, timings_path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--year", type=int, action="append")
    parser.add_argument("--type", choices=list(EXAM_TYPES), action="append")
    parser.add_argument("--force", action="store_true", help="이미 적재된 회차도 다시 처리")
    parser.add_argument("--psql", default=os.environ.get("PSQL_CMD", DEFAULT_PSQL))
    args = parser.parse_args()

    if not args.all and not args.year and not args.type:
        parser.error("--all 또는 --year/--type 중 하나는 지정해야 합니다")

    env = load_env()
    exams = discover_exams(env)
    if args.year:
        exams = [e for e in exams if e.year in args.year]
    if args.type:
        exams = [e for e in exams if e.folder in args.type]

    if not exams:
        sys.exit("처리할 회차가 없습니다.")

    print(f"대상 {len(exams)}개 회차\n")
    results: list[tuple[Exam, str, list[str]]] = []

    for index, exam in enumerate(exams, start=1):
        started = time.time()
        print(f"[{index}/{len(exams)}] {exam.title} … ", end="", flush=True)
        status, warnings = process(env, exam, args.psql, args.force)
        print(f"{status} ({time.time() - started:.0f}초)")
        for warning in warnings:
            print(f"      ⚠️  {warning}")
        results.append((exam, status, warnings))

    print("\n" + "=" * 60)
    done = [r for r in results if r[1] == "완료"]
    skipped = [r for r in results if r[1].startswith("건너뜀")]
    failed = [r for r in results if r[1].startswith("실패")]
    flagged = [r for r in results if r[2]]

    print(f"완료 {len(done)} · 건너뜀 {len(skipped)} · 실패 {len(failed)}")
    if failed:
        print("\n실패한 회차:")
        for exam, status, _ in failed:
            print(f"  {exam.slug}: {status}")
    if flagged:
        print("\n확인이 필요한 회차:")
        for exam, _, warnings in flagged:
            print(f"  {exam.slug}: {len(warnings)}건")


if __name__ == "__main__":
    main()
