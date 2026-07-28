-- 숙제를 학생 단위로 배포할 수 있게 한다.
--
-- 배경: 현재는 1:1 과외라 '반'이 없다. V1 은 숙제가 반 단위로만 나가도록
-- (class_id NOT NULL) 돼 있어 반을 억지로 만들어야 했다.
-- classes / class_members 테이블은 그대로 두고, 규모가 커지면 그때 다시 켠다.
--
-- 대상 규칙
--   student_id 지정  → 그 학생에게만
--   class_id 지정    → 그 반 학생 전체 (지금은 미사용)
--   둘 다 NULL       → 전체 학생 (공지형 과제)

ALTER TABLE assignments ALTER COLUMN class_id DROP NOT NULL;

ALTER TABLE assignments ADD COLUMN student_id uuid REFERENCES users (id) ON DELETE CASCADE;

-- 반과 학생을 동시에 지정하면 대상이 모호해진다
ALTER TABLE assignments ADD CONSTRAINT assignments_target_check
    CHECK (class_id IS NULL OR student_id IS NULL);

CREATE INDEX idx_assignments_student_due ON assignments (student_id, due_date DESC);

-- 캘린더 뷰: 학생 지정 / 반 소속 / 전체 대상 세 경우를 모두 반영한다
DROP VIEW IF EXISTS v_homework_calendar;

CREATE VIEW v_homework_calendar AS
SELECT a.id                                         AS assignment_id,
       a.title,
       a.subject,
       a.assigned_date,
       a.due_date,
       u.id                                         AS student_id,
       COALESCE(s.status, 'NOT_SUBMITTED')          AS status,
       (a.due_date < CURRENT_DATE AND s.id IS NULL) AS is_overdue
FROM assignments a
         JOIN users u
              ON u.role = 'STUDENT'
                  AND u.status = 'ACTIVE'
                  AND (
                      a.student_id = u.id
                          OR (a.student_id IS NULL AND a.class_id IS NULL)
                          OR (a.class_id IS NOT NULL AND EXISTS (SELECT 1
                                                                 FROM class_members cm
                                                                 WHERE cm.class_id = a.class_id
                                                                   AND cm.student_id = u.id))
                      )
         LEFT JOIN homework_submissions s
                   ON s.assignment_id = a.id AND s.student_id = u.id;
