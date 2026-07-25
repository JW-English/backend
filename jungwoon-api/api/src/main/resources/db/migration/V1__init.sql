-- 정운영어 초기 스키마 (기획안 4장)
-- 원칙
--  1) 모든 콘텐츠 테이블은 subject 컬럼을 가진다 → 국어/수학 확장 시 스키마 변경 없음
--  2) 권한 판단은 애플리케이션(Spring Security)이 한다. RLS 미사용
--  3) 스키마의 단일 소스는 이 파일이다. Hibernate ddl-auto 는 validate 고정

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- =========================================================
-- 1. 계정 · 공통
-- =========================================================

CREATE TABLE users (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text        UNIQUE,                      -- 소셜 전용 계정은 NULL
    password_hash text,                                    -- BCrypt(12). 소셜 전용이면 NULL
    role          text        NOT NULL DEFAULT 'STUDENT',
    name          text        NOT NULL,
    phone         text,
    grade         int,                                     -- 1,2,3 (고1~고3)
    school        text,
    avatar_key    text,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    onboarded_at  timestamptz,
    last_login_at timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check   CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'WITHDRAWN')),
    CONSTRAINT users_grade_check  CHECK (grade IS NULL OR grade BETWEEN 1 AND 3)
);
CREATE INDEX idx_users_role_status ON users (role, status);

CREATE TABLE social_accounts (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    text        NOT NULL,
    provider_id text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT social_accounts_provider_check CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER', 'APPLE')),
    CONSTRAINT uq_social_provider_id UNIQUE (provider, provider_id)
);
CREATE INDEX idx_social_accounts_user ON social_accounts (user_id);

-- 반(클래스) — 숙제/단어 배포 단위
CREATE TABLE classes (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text        NOT NULL,
    subject     text        NOT NULL DEFAULT 'ENGLISH',
    grade       int,
    teacher_id  uuid        REFERENCES users (id) ON DELETE SET NULL,
    invite_code text        NOT NULL UNIQUE,               -- 학생이 입력하는 반 코드
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT classes_subject_check CHECK (subject IN ('ENGLISH', 'KOREAN', 'MATH'))
);
CREATE INDEX idx_classes_teacher ON classes (teacher_id);

CREATE TABLE class_members (
    class_id   uuid        NOT NULL REFERENCES classes (id) ON DELETE CASCADE,
    student_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (class_id, student_id)
);
CREATE INDEX idx_class_members_student ON class_members (student_id);

-- 푸시 토큰
CREATE TABLE devices (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expo_push_token text        NOT NULL UNIQUE,
    platform        text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT devices_platform_check CHECK (platform IN ('IOS', 'ANDROID'))
);
CREATE INDEX idx_devices_user ON devices (user_id);

CREATE TABLE notifications (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text        NOT NULL,                       -- HOMEWORK_COMMENT | QNA_ANSWERED | ...
    title      text        NOT NULL,
    body       text,
    link       text,                                       -- 앱 딥링크
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

-- 관리자 감사 로그 (성적 수정, 계정 변경, 게시물 삭제)
CREATE TABLE audit_logs (
    id          bigserial   PRIMARY KEY,
    actor_id    uuid        REFERENCES users (id) ON DELETE SET NULL,
    action      text        NOT NULL,
    target_type text,
    target_id   text,
    payload     jsonb,
    ip          inet,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id, created_at DESC);

-- =========================================================
-- 2. 숙제
-- =========================================================

CREATE TABLE assignments (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id      uuid        NOT NULL REFERENCES classes (id) ON DELETE CASCADE,
    subject       text        NOT NULL DEFAULT 'ENGLISH',
    title         text        NOT NULL,
    description   text,
    assigned_date date        NOT NULL,
    due_date      date        NOT NULL,
    created_by    uuid        REFERENCES users (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT assignments_subject_check CHECK (subject IN ('ENGLISH', 'KOREAN', 'MATH')),
    CONSTRAINT assignments_date_check    CHECK (due_date >= assigned_date)
);
CREATE INDEX idx_assignments_class_due ON assignments (class_id, due_date DESC);

CREATE TABLE homework_submissions (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id uuid        NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    student_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        text        NOT NULL DEFAULT 'SUBMITTED',
    submitted_at  timestamptz NOT NULL DEFAULT now(),
    reviewed_at   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT homework_submissions_status_check
        CHECK (status IN ('SUBMITTED', 'REVIEWED', 'RESUBMIT_REQUIRED')),
    CONSTRAINT uq_submission_per_student UNIQUE (assignment_id, student_id)
);
-- 학생 마이페이지·캘린더 조회 경로
CREATE INDEX idx_homework_submissions_student ON homework_submissions (student_id, assignment_id);

CREATE TABLE submission_images (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id uuid        NOT NULL REFERENCES homework_submissions (id) ON DELETE CASCADE,
    storage_key   text        NOT NULL,
    sort_order    int         NOT NULL DEFAULT 0,
    width         int,
    height        int,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_submission_images_submission ON submission_images (submission_id, sort_order);

CREATE TABLE submission_comments (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id uuid        NOT NULL REFERENCES homework_submissions (id) ON DELETE CASCADE,
    author_id     uuid        REFERENCES users (id) ON DELETE SET NULL,
    body          text,
    image_key     text,                                    -- 선생님 첨삭 이미지
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT submission_comments_content_check CHECK (body IS NOT NULL OR image_key IS NOT NULL)
);
CREATE INDEX idx_submission_comments_submission ON submission_comments (submission_id, created_at);

-- 캘린더 화면 전용 뷰: 반에 속한 학생 x 숙제 조합에 제출 상태를 얹는다
CREATE VIEW v_homework_calendar AS
SELECT a.id                                              AS assignment_id,
       a.class_id,
       a.title,
       a.assigned_date,
       a.due_date,
       cm.student_id,
       COALESCE(s.status, 'NOT_SUBMITTED')               AS status,
       (a.due_date < CURRENT_DATE AND s.id IS NULL)      AS is_overdue
FROM assignments a
         JOIN class_members cm ON cm.class_id = a.class_id
         LEFT JOIN homework_submissions s
                   ON s.assignment_id = a.id AND s.student_id = cm.student_id;

-- =========================================================
-- 3. 단어
-- =========================================================

CREATE TABLE words (
    id         bigserial   PRIMARY KEY,
    headword   text        NOT NULL,
    meaning_ko text        NOT NULL,                       -- 대표 뜻
    meanings   jsonb,                                      -- [{"pos":"v","ko":"..."}]
    example_en text,
    example_ko text,
    audio_key  text,
    level      int,
    tags       text[],
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_word_headword_meaning UNIQUE (headword, meaning_ko)
);
CREATE INDEX idx_words_headword ON words (headword);
CREATE INDEX idx_words_level ON words (level);

-- 학년별 날짜 단위 학습 묶음 ("DAY")
CREATE TABLE word_days (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    grade          int         NOT NULL,
    day_no         int         NOT NULL,
    scheduled_date date,                                   -- 이 날짜부터 열린다
    title          text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT word_days_grade_check CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT uq_word_day UNIQUE (grade, day_no)
);
CREATE INDEX idx_word_days_grade_date ON word_days (grade, scheduled_date);

CREATE TABLE word_day_items (
    day_id     uuid   NOT NULL REFERENCES word_days (id) ON DELETE CASCADE,
    word_id    bigint NOT NULL REFERENCES words (id) ON DELETE CASCADE,
    sort_order int    NOT NULL DEFAULT 0,
    PRIMARY KEY (day_id, word_id)
);
CREATE INDEX idx_word_day_items_word ON word_day_items (word_id);

CREATE TABLE quiz_attempts (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day_id        uuid        NOT NULL REFERENCES word_days (id) ON DELETE CASCADE,
    started_at    timestamptz NOT NULL DEFAULT now(),
    finished_at   timestamptz,                             -- NOT NULL 이면 재제출 거부
    total_count   int         NOT NULL DEFAULT 0,
    correct_count int         NOT NULL DEFAULT 0,
    score         numeric     GENERATED ALWAYS AS (
        CASE WHEN total_count = 0 THEN 0
             ELSE correct_count::numeric / total_count * 100 END
    ) STORED
);
CREATE INDEX idx_quiz_attempts_student_day ON quiz_attempts (student_id, day_id);

CREATE TABLE quiz_answers (
    id             bigserial PRIMARY KEY,
    attempt_id     uuid      NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    word_id        bigint    NOT NULL REFERENCES words (id) ON DELETE CASCADE,
    question_type  text      NOT NULL,                     -- EN_TO_KO | KO_TO_EN
    choices        jsonb     NOT NULL,                     -- 출제된 보기 (재현용)
    correct_index  int       NOT NULL,                     -- 서버 전용. 응답 DTO 에 절대 넣지 않는다
    selected_index int,
    is_correct     boolean,
    sort_order     int       NOT NULL DEFAULT 0,
    answered_at    timestamptz,
    CONSTRAINT quiz_answers_type_check CHECK (question_type IN ('EN_TO_KO', 'KO_TO_EN')),
    CONSTRAINT uq_quiz_answer UNIQUE (attempt_id, word_id)
);
CREATE INDEX idx_quiz_answers_attempt ON quiz_answers (attempt_id, sort_order);

-- 오답노트 (누적)
CREATE TABLE wrong_notes (
    student_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    word_id       bigint      NOT NULL REFERENCES words (id) ON DELETE CASCADE,
    wrong_count   int         NOT NULL DEFAULT 1,
    streak_count  int         NOT NULL DEFAULT 0,          -- 연속 정답 수 (3회면 졸업)
    last_wrong_at timestamptz NOT NULL DEFAULT now(),
    next_review_at timestamptz,                            -- 간격 반복(3·7·14일)
    mastered_at   timestamptz,
    PRIMARY KEY (student_id, word_id)
);
CREATE INDEX idx_wrong_notes_review ON wrong_notes (student_id, next_review_at) WHERE mastered_at IS NULL;

-- =========================================================
-- 4. 리스닝
-- =========================================================

CREATE TABLE exams (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    year       int         NOT NULL,
    exam_type  text        NOT NULL,                       -- SUNEUNG | MOCK_9 | MOCK_6 | MOCK_3 | EDU_OFFICE
    grade      int         NOT NULL,
    title      text        NOT NULL,
    audio_key  text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT exams_type_check CHECK (exam_type IN ('SUNEUNG', 'MOCK_9', 'MOCK_6', 'MOCK_3', 'EDU_OFFICE')),
    CONSTRAINT uq_exam UNIQUE (year, exam_type, grade)
);

CREATE TABLE listening_items (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id       uuid        NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    item_no       int         NOT NULL,                    -- 1~17
    item_type     text,                                    -- 목적 | 주제 | 그림불일치 ...
    question_text text,
    choices       jsonb,
    answer_index  int,                                     -- 서버 전용
    audio_key     text        NOT NULL,
    duration_ms   int,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_listening_item UNIQUE (exam_id, item_no)
);

CREATE TABLE listening_sentences (
    id       bigserial PRIMARY KEY,
    item_id  uuid      NOT NULL REFERENCES listening_items (id) ON DELETE CASCADE,
    seq      int       NOT NULL,
    speaker  text,                                         -- M | W | NARRATOR
    text_en  text      NOT NULL,
    text_ko  text,
    start_ms int       NOT NULL,
    end_ms   int       NOT NULL,
    CONSTRAINT listening_sentences_range_check CHECK (end_ms > start_ms),
    CONSTRAINT uq_listening_sentence UNIQUE (item_id, seq)
);
CREATE INDEX idx_listening_sentences_item ON listening_sentences (item_id, seq);

CREATE TABLE listening_progress (
    student_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_id          uuid NOT NULL REFERENCES listening_items (id) ON DELETE CASCADE,
    last_position_ms int  NOT NULL DEFAULT 0,
    play_count       int  NOT NULL DEFAULT 0,
    completed_at     timestamptz,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, item_id)
);

CREATE TABLE listening_bookmarks (
    id          bigserial   PRIMARY KEY,
    student_id  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sentence_id bigint      NOT NULL REFERENCES listening_sentences (id) ON DELETE CASCADE,
    memo        text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_listening_bookmark UNIQUE (student_id, sentence_id)
);

-- =========================================================
-- 5. Q&A
-- =========================================================

CREATE TABLE questions (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject    text        NOT NULL DEFAULT 'ENGLISH',
    title      text        NOT NULL,
    body       text        NOT NULL,
    is_public  boolean     NOT NULL DEFAULT false,
    status     text        NOT NULL DEFAULT 'OPEN',
    view_count int         NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT questions_subject_check CHECK (subject IN ('ENGLISH', 'KOREAN', 'MATH')),
    CONSTRAINT questions_status_check  CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED'))
);
CREATE INDEX idx_questions_public ON questions (is_public, created_at DESC);
CREATE INDEX idx_questions_author ON questions (author_id, created_at DESC);
CREATE INDEX idx_questions_open ON questions (created_at) WHERE status = 'OPEN';

CREATE TABLE question_images (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    storage_key text        NOT NULL,
    sort_order  int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_images_question ON question_images (question_id, sort_order);

CREATE TABLE answers (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    author_id   uuid        REFERENCES users (id) ON DELETE SET NULL,
    body        text        NOT NULL,
    is_teacher  boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_answers_question ON answers (question_id, created_at);

CREATE TABLE answer_images (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id   uuid        NOT NULL REFERENCES answers (id) ON DELETE CASCADE,
    storage_key text        NOT NULL,
    sort_order  int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_answer_images_answer ON answer_images (answer_id, sort_order);

-- =========================================================
-- 6. 인강 (P6)
-- =========================================================

CREATE TABLE courses (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject       text        NOT NULL DEFAULT 'ENGLISH',
    grade         int,
    title         text        NOT NULL,
    description   text,
    thumbnail_key text,
    teacher_id    uuid        REFERENCES users (id) ON DELETE SET NULL,
    is_published  boolean     NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT courses_subject_check CHECK (subject IN ('ENGLISH', 'KOREAN', 'MATH'))
);

CREATE TABLE lessons (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       uuid        NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    sort_order      int         NOT NULL DEFAULT 0,
    title           text        NOT NULL,
    video_asset_id  text,                                  -- Mux asset id
    duration_ms     int,
    is_free_preview boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_lessons_course ON lessons (course_id, sort_order);

CREATE TABLE lesson_progress (
    student_id       uuid    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lesson_id        uuid    NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    last_position_ms int     NOT NULL DEFAULT 0,
    watched_ratio    numeric NOT NULL DEFAULT 0,
    completed_at     timestamptz,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, lesson_id)
);

CREATE TABLE lesson_materials (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id  uuid        NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    title      text        NOT NULL,
    file_key   text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_lesson_materials_lesson ON lesson_materials (lesson_id);
