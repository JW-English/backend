-- 영어 문법 위키. 30챕터 · 253용어.
--
-- 콘텐츠가 자주 바뀌지 않지만 선생님이 관리자 화면에서 고칠 수 있게 DB 에 둔다.
-- 학생 쪽은 읽기 전용이다.

CREATE TABLE wiki_chapters (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_no int         NOT NULL,
    title      text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_wiki_chapter_no UNIQUE (chapter_no)
);

CREATE TABLE wiki_terms (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id  uuid        NOT NULL REFERENCES wiki_chapters (id) ON DELETE CASCADE,
    sort_order  int         NOT NULL,

    name        text        NOT NULL,               -- 한국어 용어명
    name_en     text,                               -- 영문명. 8장 조동사처럼 없는 챕터가 있다
    description text        NOT NULL,

    -- "ex) run, eat, know" 목록. 용어의 1/4 만 가지고 있어 별도 테이블로 만들 만큼
    -- 크지 않다. 순서가 의미를 가지므로 배열로 둔다
    usages      text[],

    -- 예문과 해석은 짝이다. 비교 예문이 있는 용어는 2줄씩 들어간다
    -- (예: "I stopped smoking." / "I stopped to smoke.")
    examples    text[]      NOT NULL,
    meanings    text[]      NOT NULL,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_wiki_term_order UNIQUE (chapter_id, sort_order),
    -- 예문 하나에 해석 하나. 어긋나면 화면에서 짝이 밀린다
    CONSTRAINT ck_wiki_example_pairs CHECK (cardinality(examples) = cardinality(meanings))
);

CREATE INDEX idx_wiki_terms_chapter ON wiki_terms (chapter_id, sort_order);

-- 검색.
--
-- to_tsvector 의 한국어 형태소 분석은 기본 제공되지 않는다. 대신 대소문자·부분
-- 일치를 ILIKE 로 처리하고 pg_trgm 인덱스로 받친다 — 253행이라 성능은 문제가
-- 아니지만, 인덱스가 있어야 '%수동%' 같은 앞뒤 와일드카드가 순차 스캔을 타지 않는다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_wiki_terms_search ON wiki_terms
    USING gin ((name || ' ' || coalesce(name_en, '') || ' ' || description) gin_trgm_ops);
