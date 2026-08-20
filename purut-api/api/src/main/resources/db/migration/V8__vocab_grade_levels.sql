-- 어휘 레벨을 학년 이름으로 바꾼다.
--
-- V5 에서 BEGINNER/INTERMEDIATE/ADVANCED 로 나눴는데, 실제 교재가 고1·고2·고3
-- 세 벌로 만들어져 이름을 맞춘다.
--
-- 학생별 vocab_level 지정은 그대로 둔다. 이름만 학년을 따를 뿐 users.grade 와는
-- 여전히 별개다 — 고3 학생에게 고1 단어장을 지정할 수 있어야 한다. V5 에서
-- 둘을 분리한 이유가 그것이다.

-- ── 1) 기존 데이터 정리
-- 교재가 통째로 바뀐다. 옛 단어를 남기면 (level, day_no) 를 선점해 충돌하고,
-- 어느 쪽이 현재 교재인지도 알 수 없다.
-- 응시 기록·오답노트는 이 단어들을 참조하므로 FK CASCADE 로 함께 정리된다.
--
-- 다만 questions.ref_word_day_id 는 CASCADE 가 아니다. 질문 본문은 남아야 하므로
-- (사라진 교재를 가리켰다고 학생 질문을 지울 수는 없다) 참조만 끊는다.
UPDATE questions SET ref_word_day_id = NULL WHERE ref_word_day_id IS NOT NULL;

DELETE FROM word_days;
DELETE FROM words;

-- ── 2) 레벨 값 교체
ALTER TABLE word_days DROP CONSTRAINT word_days_level_check;
ALTER TABLE users     DROP CONSTRAINT users_vocab_level_check;

UPDATE word_days SET level = CASE level
    WHEN 'BEGINNER'     THEN 'GRADE_1'
    WHEN 'INTERMEDIATE' THEN 'GRADE_2'
    WHEN 'ADVANCED'     THEN 'GRADE_3'
    ELSE 'GRADE_1'
END;

UPDATE users SET vocab_level = CASE vocab_level
    WHEN 'BEGINNER'     THEN 'GRADE_1'
    WHEN 'INTERMEDIATE' THEN 'GRADE_2'
    WHEN 'ADVANCED'     THEN 'GRADE_3'
    ELSE vocab_level
END
WHERE vocab_level IS NOT NULL;

ALTER TABLE word_days ADD CONSTRAINT word_days_level_check
    CHECK (level IN ('GRADE_1', 'GRADE_2', 'GRADE_3'));
ALTER TABLE users ADD CONSTRAINT users_vocab_level_check
    CHECK (vocab_level IS NULL OR vocab_level IN ('GRADE_1', 'GRADE_2', 'GRADE_3'));

-- ── 3) 품사
-- 새 교재는 표제어마다 품사가 있다(noun, verb, adj …). 기존에는 tags 배열에
-- 'headword'/'derived' 를 넣어 썼는데 의미가 다르다. 별도 컬럼으로 둔다.
ALTER TABLE words ADD COLUMN part_of_speech text;
