-- 단어 DAY 를 학교 학년에서 분리해 어휘 레벨로 묶는다.
--
-- 기존에는 word_days.grade(1~3) 가 학교 학년이자 난이도를 겸했고,
-- DAY 목록 기본값이 학생의 학년이었다. 1:1 과외에서는 고3이 beginner 를
-- 봐야 하는 경우가 흔한데 그 구조로는 기본 화면에서 자기 레벨이 안 나온다.
--
-- level 을 별도 축으로 두고, 학생마다 vocab_level 을 지정한다.

-- ── 1) 어휘 레벨 컬럼
ALTER TABLE word_days ADD COLUMN level text;

-- 기존 데이터 이관 (더미뿐이지만 규칙을 남겨둔다)
UPDATE word_days SET level = CASE grade
    WHEN 1 THEN 'BEGINNER'
    WHEN 2 THEN 'INTERMEDIATE'
    WHEN 3 THEN 'ADVANCED'
    ELSE 'INTERMEDIATE'
END;

ALTER TABLE word_days ALTER COLUMN level SET NOT NULL;
ALTER TABLE word_days ADD CONSTRAINT word_days_level_check
    CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'));

-- ── 2) grade 를 걷어낸다
-- level 이 그 역할을 대신하므로 남겨두면 어느 쪽이 기준인지 헷갈린다.
ALTER TABLE word_days DROP CONSTRAINT uq_word_day;            -- (grade, day_no)
ALTER TABLE word_days DROP CONSTRAINT word_days_grade_check;
DROP INDEX IF EXISTS idx_word_days_grade_date;
ALTER TABLE word_days DROP COLUMN grade;

ALTER TABLE word_days ADD CONSTRAINT uq_word_day UNIQUE (level, day_no);
CREATE INDEX idx_word_days_level_date ON word_days (level, scheduled_date);

-- ── 3) 학생별 어휘 레벨
-- users.grade 는 학교 학년으로 그대로 남긴다 (온보딩·통계에 쓴다).
ALTER TABLE users ADD COLUMN vocab_level text;
ALTER TABLE users ADD CONSTRAINT users_vocab_level_check
    CHECK (vocab_level IS NULL OR vocab_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'));

-- 아직 지정하지 않은 학생은 학년에서 추정해 둔다. 이후 선생님이 조정한다
UPDATE users SET vocab_level = CASE grade
    WHEN 1 THEN 'BEGINNER'
    WHEN 2 THEN 'INTERMEDIATE'
    WHEN 3 THEN 'ADVANCED'
    ELSE NULL
END
WHERE role = 'STUDENT' AND vocab_level IS NULL;

-- ── 4) 더미 단어 정리
-- 실데이터를 적재하기로 했다. 더미가 (level, day_no) 를 선점하면 충돌한다.
-- 응시 기록도 이 단어들을 참조하므로 함께 정리된다 (FK ON DELETE CASCADE).
DELETE FROM word_days;
DELETE FROM words;
