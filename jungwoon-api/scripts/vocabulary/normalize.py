"""표제어 정규화. generate.py 와 build_seed.py 가 같이 쓴다.

두 곳이 어긋나면 생성 캐시 키와 적재 표제어가 달라져 예문이 붙지 않는다.

원본 CSV 는 손대지 않는다. 추출 결과는 자료 그대로 남겨두고 여기서만 다듬는다.
"""

from __future__ import annotations

import re

# "behavior(behaviour)" — 미·영 철자 변형. 공백이 없는 형태만 잡는다.
# "(the) chances are (that)", "have difficulty (in) -ing" 처럼 괄호가
# 생략 가능 요소를 뜻하는 표기는 건드리면 안 된다
SPELLING_VARIANT = re.compile(r"^([A-Za-z]+)\(([A-Za-z]+)\)$")

# "lie1", "lie2" — 동음이의어 구분용 첨자. 단어의 일부가 아니다.
# 학생 화면에 그대로 나가면 오타로 보인다
HOMOGRAPH_SUFFIX = re.compile(r"^([a-z]{2,})[12]$")

# "keep A from ?ing" — PDF 글리프에 유니코드 매핑이 없어 물음표로 떨어졌다.
# 같은 자료의 다른 숙어들이 "end up -ing" 를 쓴다
BROKEN_ING = re.compile(r"\?(?=ing\b)")


def normalize_headword(word: str) -> str:
    word = re.sub(r"\s+", " ", word).strip()
    word = BROKEN_ING.sub("-", word)

    m = SPELLING_VARIANT.match(word)
    if m:
        word = m.group(1)

    m = HOMOGRAPH_SUFFIX.match(word)
    if m:
        word = m.group(1)

    return word
