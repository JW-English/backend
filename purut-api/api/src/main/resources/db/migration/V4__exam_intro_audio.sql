-- 전체 듣기(인트로 → 1번 → … → 마지막) 재생을 위해 회차별 안내 방송 음원 키를 채운다.
--
-- exams.audio_key 는 V1 부터 있었으나 적재 스크립트가 채우지 않아 비어 있었다.
-- 키 규칙은 ingest_exam.py 와 같다: listening/{year}/{exam_type 소문자}/intro.mp3
-- R2 에 24회차 모두 intro.mp3 가 있는 것을 확인했다.

UPDATE exams
SET audio_key = 'listening/' || year || '/' || lower(exam_type) || '/intro.mp3'
WHERE audio_key IS NULL OR audio_key = '';
