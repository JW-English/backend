-- Q&A v1. 기존 V1 테이블을 실제 구현 스키마로 교체한다.

ALTER TABLE questions
    ADD COLUMN category text NOT NULL DEFAULT 'ETC',
    ADD COLUMN ref_exam_id uuid REFERENCES exams (id),
    ADD COLUMN ref_item_no int,
    ADD COLUMN ref_word_day_id uuid REFERENCES word_days (id),
    ADD COLUMN ref_assignment_id uuid REFERENCES assignments (id),
    ADD COLUMN ref_textbook text,
    ADD COLUMN ref_page int,
    ADD COLUMN answered_at timestamptz,
    ADD COLUMN deleted_at timestamptz;

ALTER TABLE questions DROP CONSTRAINT questions_status_check;
ALTER TABLE questions ADD CONSTRAINT questions_status_check
    CHECK (status IN ('PENDING', 'ANSWERED', 'REOPENED', 'CLOSED'));
ALTER TABLE questions ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE questions ADD CONSTRAINT questions_category_check
    CHECK (category IN ('HOMEWORK', 'VOCAB', 'LISTENING', 'TEXTBOOK', 'ETC'));

DROP TABLE answer_images;
DROP TABLE answers;
DROP TABLE question_images;

DROP INDEX IF EXISTS idx_questions_open;
CREATE INDEX idx_q_pending ON questions (created_at)
    WHERE status IN ('PENDING', 'REOPENED') AND deleted_at IS NULL;
CREATE INDEX idx_questions_visible ON questions (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE question_messages (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    author_id   uuid        NOT NULL REFERENCES users (id),
    role        text        NOT NULL,
    body        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    CONSTRAINT qm_role_check CHECK (role IN ('STUDENT', 'TEACHER'))
);
CREATE INDEX idx_qm_thread ON question_messages (question_id, created_at);

CREATE TABLE question_attachments (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid        REFERENCES questions (id) ON DELETE CASCADE,
    message_id  uuid        REFERENCES question_messages (id) ON DELETE CASCADE,
    storage_key text        NOT NULL,
    mime_type   text        NOT NULL,
    byte_size   int         NOT NULL,
    width       int,
    height      int,
    sort_order  int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT qa_owner_check CHECK (num_nonnulls(question_id, message_id) = 1),
    CONSTRAINT qa_mime_check CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/gif', 'application/pdf')),
    CONSTRAINT qa_byte_size_check CHECK (byte_size > 0 AND byte_size <= 10485760)
);
CREATE INDEX idx_qa_question ON question_attachments (question_id, sort_order);
CREATE INDEX idx_qa_message ON question_attachments (message_id, sort_order);

CREATE TABLE qna_notices (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    title      text        NOT NULL,
    body       text        NOT NULL,
    is_pinned  boolean     NOT NULL DEFAULT true,
    starts_at  timestamptz,
    ends_at    timestamptz,
    created_by uuid        NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_qna_notices_active ON qna_notices (is_pinned, starts_at, ends_at);
