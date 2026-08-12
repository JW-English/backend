-- 데모용 더미 단어 데이터.
--
-- Flyway 마이그레이션이 아니라 개발자가 직접 실행하는 스크립트다 —
-- 마이그레이션에 넣으면 운영 DB 에도 더미 데이터가 올라간다.
--
-- 실행:
--   docker exec -i jungwoon-postgres psql -U jungwoon -d jungwoon < scripts/seed-demo-words.sql
--
-- 실제 단어 DB 는 CSV 로 따로 구축해 적재한다. 이 스크립트는 화면 확인용이다.

INSERT INTO words (headword, meaning_ko, example_en, example_ko, level) VALUES
    ('abandon', '버리다, 포기하다', 'He abandoned his old car.', '그는 낡은 차를 버렸다.', 1),
    ('acquire', '습득하다, 얻다', 'She acquired a new skill.', '그녀는 새로운 기술을 습득했다.', 1),
    ('adequate', '충분한, 적절한', 'The room is adequate for four people.', '그 방은 네 명에게 충분하다.', 1),
    ('anticipate', '예상하다', 'We anticipate heavy rain.', '우리는 폭우를 예상한다.', 1),
    ('apparent', '분명한, 겉보기의', 'It became apparent that he was lying.', '그가 거짓말한다는 게 분명해졌다.', 1),
    ('assume', '가정하다, 떠맡다', 'I assume you are ready.', '나는 네가 준비됐다고 가정한다.', 1),
    ('barrier', '장벽, 장애물', 'Language can be a barrier.', '언어는 장벽이 될 수 있다.', 1),
    ('capable', '능력 있는', 'She is capable of solving it.', '그녀는 그것을 풀 능력이 있다.', 1),
    ('circumstance', '상황, 환경', 'Under no circumstances should you leave.', '어떤 상황에서도 떠나면 안 된다.', 1),
    ('comprehend', '이해하다', 'I cannot comprehend his choice.', '나는 그의 선택을 이해할 수 없다.', 1),
    ('consequence', '결과, 영향', 'Every action has a consequence.', '모든 행동에는 결과가 따른다.', 1),
    ('constant', '끊임없는, 일정한', 'He is under constant pressure.', '그는 끊임없는 압박을 받는다.', 1),
    ('crucial', '결정적인, 중대한', 'This is a crucial moment.', '지금이 결정적인 순간이다.', 1),
    ('decline', '감소하다, 거절하다', 'Sales declined last year.', '작년에 매출이 감소했다.', 1),
    ('demonstrate', '증명하다, 보여주다', 'The data demonstrates the trend.', '그 자료가 추세를 보여준다.', 1),
    ('distinct', '뚜렷한, 별개의', 'There is a distinct difference.', '뚜렷한 차이가 있다.', 1),
    ('eliminate', '제거하다', 'We must eliminate the error.', '우리는 그 오류를 제거해야 한다.', 1),
    ('emerge', '나타나다, 드러나다', 'A new problem emerged.', '새로운 문제가 나타났다.', 1),
    ('enhance', '향상시키다', 'Sleep enhances memory.', '수면은 기억력을 향상시킨다.', 1),
    ('essential', '필수적인', 'Water is essential for life.', '물은 생명에 필수적이다.', 1),

    ('evident', '명백한', 'His talent is evident.', '그의 재능은 명백하다.', 2),
    ('exceed', '초과하다, 넘다', 'Do not exceed the speed limit.', '제한 속도를 초과하지 마라.', 2),
    ('facilitate', '촉진하다, 돕다', 'Music facilitates learning.', '음악은 학습을 돕는다.', 2),
    ('fundamental', '근본적인', 'This is a fundamental right.', '이것은 근본적인 권리다.', 2),
    ('generate', '발생시키다, 만들다', 'The plant generates power.', '그 발전소는 전력을 생산한다.', 2),
    ('implement', '시행하다', 'They implemented a new rule.', '그들은 새 규칙을 시행했다.', 2),
    ('inevitable', '피할 수 없는', 'Change is inevitable.', '변화는 피할 수 없다.', 2),
    ('initiate', '시작하다, 착수하다', 'She initiated the project.', '그녀가 그 프로젝트를 시작했다.', 2),
    ('integrate', '통합하다', 'We integrated the systems.', '우리는 시스템을 통합했다.', 2),
    ('interpret', '해석하다', 'How do you interpret this poem?', '이 시를 어떻게 해석하니?', 2),
    ('maintain', '유지하다, 주장하다', 'He maintains a healthy diet.', '그는 건강한 식단을 유지한다.', 2),
    ('modify', '수정하다', 'We modified the design.', '우리는 설계를 수정했다.', 2),
    ('obtain', '얻다, 획득하다', 'She obtained permission.', '그녀는 허가를 얻었다.', 2),
    ('perceive', '인식하다, 감지하다', 'We perceive colors differently.', '우리는 색을 다르게 인식한다.', 2),
    ('persist', '지속하다, 고집하다', 'The problem persists.', '그 문제는 계속된다.', 2),
    ('reluctant', '꺼리는, 주저하는', 'He was reluctant to speak.', '그는 말하기를 꺼렸다.', 2),
    ('resolve', '해결하다, 결심하다', 'They resolved the conflict.', '그들은 갈등을 해결했다.', 2),
    ('significant', '중요한, 상당한', 'There is a significant gap.', '상당한 격차가 있다.', 2),
    ('sufficient', '충분한', 'We have sufficient time.', '우리는 충분한 시간이 있다.', 2),
    ('tolerate', '참다, 견디다', 'I cannot tolerate the noise.', '나는 그 소음을 참을 수 없다.', 2)
ON CONFLICT (headword, meaning_ko) DO NOTHING;

-- 고2 기준 DAY 2개. 예약일이 지난 것만 학생에게 열린다
INSERT INTO word_days (grade, day_no, scheduled_date, title) VALUES
    (2, 1, CURRENT_DATE - 7, 'DAY 1 — 수능 빈출 어휘 (1)'),
    (2, 2, CURRENT_DATE - 1, 'DAY 2 — 수능 빈출 어휘 (2)'),
    (2, 3, CURRENT_DATE + 7, 'DAY 3 — 아직 공개 전')
ON CONFLICT (grade, day_no) DO NOTHING;

-- level 1 → DAY 1, level 2 → DAY 2
INSERT INTO word_day_items (day_id, word_id, sort_order)
SELECT d.id,
       w.id,
       (row_number() OVER (ORDER BY w.headword) - 1)::int
FROM word_days d
         JOIN words w ON w.level = 1
WHERE d.grade = 2 AND d.day_no = 1
ON CONFLICT DO NOTHING;

INSERT INTO word_day_items (day_id, word_id, sort_order)
SELECT d.id,
       w.id,
       (row_number() OVER (ORDER BY w.headword) - 1)::int
FROM word_days d
         JOIN words w ON w.level = 2
WHERE d.grade = 2 AND d.day_no = 2
ON CONFLICT DO NOTHING;

SELECT d.day_no, d.title, d.scheduled_date, count(i.word_id) AS 단어수
FROM word_days d
         LEFT JOIN word_day_items i ON i.day_id = d.id
WHERE d.grade = 2
GROUP BY d.day_no, d.title, d.scheduled_date
ORDER BY d.day_no;
