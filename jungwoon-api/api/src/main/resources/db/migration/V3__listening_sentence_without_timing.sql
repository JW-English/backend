-- 싱크 전 문장을 먼저 적재할 수 있게 한다.
--
-- 배경: 대본 파싱은 즉시 끝나지만 문장별 타임스탬프는 강제 정렬 + 사람 보정을 거쳐야 한다.
-- 원래 제약(end_ms > start_ms)은 "아직 싱크하지 않은 문장"을 표현할 수 없어
-- 대본을 먼저 넣고 나중에 싱크하는 순서가 막혔다.
--
-- start_ms = end_ms = 0 을 "아직 싱크 안 됨"으로 본다.
-- 앱은 이 경우 문장 이동을 비활성화한다.

ALTER TABLE listening_sentences DROP CONSTRAINT IF EXISTS listening_sentences_range_check;

ALTER TABLE listening_sentences ADD CONSTRAINT listening_sentences_range_check
    CHECK (end_ms >= start_ms);

COMMENT ON COLUMN listening_sentences.start_ms IS '문장 시작 위치(ms). start=end=0 이면 아직 싱크 전';
COMMENT ON COLUMN listening_sentences.end_ms IS '문장 끝 위치(ms). start=end=0 이면 아직 싱크 전';
