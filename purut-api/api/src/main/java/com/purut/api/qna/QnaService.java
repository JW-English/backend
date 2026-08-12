package com.purut.api.qna;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.qna.dto.AdminQnaDtos.AdminQuestionDetail;
import com.purut.api.qna.dto.AdminQnaDtos.AdminQuestionListItem;
import com.purut.api.qna.dto.QnaDtos.AttachmentRequest;
import com.purut.api.qna.dto.QnaDtos.AttachmentUrl;
import com.purut.api.qna.dto.QnaDtos.CreateMessageRequest;
import com.purut.api.qna.dto.QnaDtos.CreateQuestionRequest;
import com.purut.api.qna.dto.QnaDtos.CursorPage;
import com.purut.api.qna.dto.QnaDtos.QnaNoticeItem;
import com.purut.api.qna.dto.QnaDtos.QuestionDetail;
import com.purut.api.qna.dto.QnaDtos.QuestionListItem;
import com.purut.api.qna.dto.QnaDtos.UpdateQuestionRequest;
import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.common.error.ForbiddenException;
import com.purut.common.error.NotFoundException;
import com.purut.domain.homework.Assignment;
import com.purut.domain.homework.AssignmentRepository;
import com.purut.domain.listening.Exam;
import com.purut.domain.listening.ExamRepository;
import com.purut.domain.listening.ListeningItemRepository;
import com.purut.domain.qna.QnaNoticeRepository;
import com.purut.domain.qna.Question;
import com.purut.domain.qna.QuestionAttachment;
import com.purut.domain.qna.QuestionAttachmentRepository;
import com.purut.domain.qna.QuestionCategory;
import com.purut.domain.qna.QuestionMessage;
import com.purut.domain.qna.QuestionMessageRepository;
import com.purut.domain.qna.QuestionMessageRole;
import com.purut.domain.qna.QuestionRepository;
import com.purut.domain.qna.QuestionStatus;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.domain.vocabulary.WordDay;
import com.purut.domain.vocabulary.WordDayRepository;
import com.purut.infra.storage.FileStorage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class QnaService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final long DOWNLOAD_EXPIRES_IN = 3600L;
    private static final Instant FIRST_DESC_CURSOR_CREATED_AT = Instant.parse("9999-12-31T23:59:59Z");
    private static final Instant FIRST_ASC_CURSOR_CREATED_AT = Instant.EPOCH;
    private static final UUID FIRST_CURSOR_ID = new UUID(0L, 0L);
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "application/pdf");

    private final QuestionRepository questionRepository;
    private final QuestionMessageRepository messageRepository;
    private final QuestionAttachmentRepository attachmentRepository;
    private final QnaNoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ListeningItemRepository listeningItemRepository;
    private final WordDayRepository wordDayRepository;
    private final AssignmentRepository assignmentRepository;
    private final FileStorage fileStorage;

    public QnaService(QuestionRepository questionRepository,
                      QuestionMessageRepository messageRepository,
                      QuestionAttachmentRepository attachmentRepository,
                      QnaNoticeRepository noticeRepository,
                      UserRepository userRepository,
                      ExamRepository examRepository,
                      ListeningItemRepository listeningItemRepository,
                      WordDayRepository wordDayRepository,
                      AssignmentRepository assignmentRepository,
                      FileStorage fileStorage) {
        this.questionRepository = questionRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.listeningItemRepository = listeningItemRepository;
        this.wordDayRepository = wordDayRepository;
        this.assignmentRepository = assignmentRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public CursorPage<QuestionListItem> list(UserPrincipal me, String scope, QuestionCategory category,
                                             QuestionStatus status, String cursor, Integer size) {
        CursorParts cursorParts = decodeCursor(cursor);
        CursorParts effectiveCursor = cursorParts.orFirstDescending();
        List<Question> questions = questionRepository.findVisiblePage(
                me.id(),
                "mine".equals(scope) ? "mine" : "public",
                category,
                status,
                effectiveCursor.createdAt,
                effectiveCursor.id,
                PageRequest.of(0, normalizeSize(size) + 1)
        );
        boolean hasNext = questions.size() > normalizeSize(size);
        List<Question> page = hasNext ? questions.subList(0, normalizeSize(size)) : questions;
        List<QuestionListItem> items = page.stream()
                .map(q -> QuestionListItem.of(q, me.id(),
                        messageRepository.countByQuestionIdAndRoleAndDeletedAtIsNull(q.getId(), QuestionMessageRole.TEACHER)))
                .toList();
        return new CursorPage<>(items, hasNext ? encodeCursor(page.getLast().getCreatedAt(), page.getLast().getId()) : null);
    }

    @Transactional
    public QuestionDetail create(UserPrincipal me, CreateQuestionRequest request) {
        validateReferenceShape(request.category(), request.refExamId(), request.refItemNo(), request.refWordDayId(),
                request.refAssignmentId(), request.refTextbook(), request.refPage());
        validateAttachments(request.attachmentsOrEmpty());

        User author = userRepository.getReferenceById(me.id());
        Exam exam = request.refExamId() == null ? null : examRepository.findById(request.refExamId())
                .orElseThrow(() -> new NotFoundException("시험을 찾을 수 없습니다."));
        if (exam != null && listeningItemRepository.findByExamIdAndItemNo(exam.getId(), request.refItemNo()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 리스닝 문항입니다.");
        }
        WordDay wordDay = request.refWordDayId() == null ? null : wordDayRepository.findById(request.refWordDayId())
                .orElseThrow(() -> new NotFoundException("단어 DAY를 찾을 수 없습니다."));
        Assignment assignment = request.refAssignmentId() == null ? null
                : assignmentRepository.findVisibleToStudent(request.refAssignmentId(), me.id())
                        .orElseThrow(() -> new NotFoundException("숙제를 찾을 수 없습니다."));

        Question question = questionRepository.save(Question.builder()
                .author(author)
                .category(request.category())
                .title(request.title())
                .body(request.body())
                .publicVisible(request.publicVisible())
                .refExam(exam)
                .refItemNo(request.refItemNo())
                .refWordDay(wordDay)
                .refAssignment(assignment)
                .refTextbook(trimToNull(request.refTextbook()))
                .refPage(request.refPage())
                .build());
        List<QuestionAttachment> attachments = saveQuestionAttachments(question, request.attachmentsOrEmpty());
        return QuestionDetail.of(question, me.id(), attachments, List.of(), Map.of());
    }

    @Transactional(readOnly = true)
    public QuestionDetail detail(UserPrincipal me, UUID questionId) {
        Question question = questionRepository.findVisibleToStudent(questionId, me.id())
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        return studentDetail(me.id(), question);
    }

    @Transactional
    public QuestionDetail update(UserPrincipal me, UUID questionId, UpdateQuestionRequest request) {
        Question question = questionRepository.findByIdAndAuthorIdAndDeletedAtIsNull(questionId, me.id())
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        if (!question.canModifyBeforeAnswer(me.id())) {
            throw new ForbiddenException("답변 후에는 질문을 수정할 수 없습니다.");
        }
        question.updateBeforeAnswer(request.title(), request.body(), request.publicVisible());
        return studentDetail(me.id(), question);
    }

    @Transactional
    public void delete(UserPrincipal me, UUID questionId) {
        Question question = questionRepository.findByIdAndAuthorIdAndDeletedAtIsNull(questionId, me.id())
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        if (!question.canModifyBeforeAnswer(me.id())) {
            throw new ForbiddenException("답변 후에는 질문을 삭제할 수 없습니다.");
        }
        question.softDelete();
    }

    @Transactional
    public QuestionDetail reopen(UserPrincipal me, UUID questionId, CreateMessageRequest request) {
        validateAttachments(request.attachmentsOrEmpty());
        Question question = questionRepository.findByIdAndAuthorIdAndDeletedAtIsNull(questionId, me.id())
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        if (question.getStatus() == QuestionStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CONFLICT, "종료된 질문에는 재질문할 수 없습니다.");
        }
        User author = userRepository.getReferenceById(me.id());
        QuestionMessage message = messageRepository.save(
                new QuestionMessage(question, author, QuestionMessageRole.STUDENT, request.body()));
        saveMessageAttachments(message, request.attachmentsOrEmpty());
        question.reopen();
        return studentDetail(me.id(), question);
    }

    @Transactional
    public QuestionDetail close(UserPrincipal me, UUID questionId) {
        Question question = questionRepository.findByIdAndAuthorIdAndDeletedAtIsNull(questionId, me.id())
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        question.close();
        return studentDetail(me.id(), question);
    }

    @Transactional(readOnly = true)
    public List<QnaNoticeItem> notices() {
        return noticeRepository.findActivePinned(Instant.now()).stream()
                .map(QnaNoticeItem::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentUrl attachmentUrl(UserPrincipal me, UUID attachmentId) {
        QuestionAttachment attachment = attachmentRepository.findReadableAttachment(attachmentId)
                .orElseThrow(() -> new NotFoundException("첨부파일을 찾을 수 없습니다."));
        Question question = attachment.getQuestion() != null
                ? attachment.getQuestion()
                : attachment.getMessage().getQuestion();
        if (!me.isTeacher() && !question.canStudentRead(me.id())) {
            throw new NotFoundException("첨부파일을 찾을 수 없습니다.");
        }
        return new AttachmentUrl(attachment.getId(), fileStorage.presignDownload(attachment.getStorageKey()),
                DOWNLOAD_EXPIRES_IN);
    }

    @Transactional(readOnly = true)
    public CursorPage<AdminQuestionListItem> adminList(QuestionStatus status, QuestionCategory category,
                                                       String cursor, Integer size) {
        CursorParts cursorParts = decodeCursor(cursor);
        CursorParts effectiveCursor = cursorParts.orFirstAscending();
        int normalizedSize = normalizeSize(size);
        List<Question> questions = questionRepository.findAdminQueue(status, category, effectiveCursor.createdAt,
                effectiveCursor.id, PageRequest.of(0, normalizedSize + 1));
        boolean hasNext = questions.size() > normalizedSize;
        List<Question> page = hasNext ? questions.subList(0, normalizedSize) : questions;
        List<AdminQuestionListItem> items = page.stream()
                .map(q -> AdminQuestionListItem.of(q,
                        messageRepository.countByQuestionIdAndRoleAndDeletedAtIsNull(q.getId(), QuestionMessageRole.TEACHER)))
                .toList();
        return new CursorPage<>(items, hasNext ? encodeCursor(page.getLast().getCreatedAt(), page.getLast().getId()) : null);
    }

    @Transactional(readOnly = true)
    public long adminPendingCount() {
        return questionRepository.countByDeletedAtIsNullAndStatusIn(List.of(QuestionStatus.PENDING, QuestionStatus.REOPENED));
    }

    @Transactional(readOnly = true)
    public AdminQuestionDetail adminDetail(UUID questionId) {
        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        return adminDetail(question);
    }

    @Transactional
    public AdminQuestionDetail answer(UserPrincipal me, UUID questionId, CreateMessageRequest request) {
        validateAttachments(request.attachmentsOrEmpty());
        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        User teacher = userRepository.getReferenceById(me.id());
        QuestionMessage message = messageRepository.save(
                new QuestionMessage(question, teacher, QuestionMessageRole.TEACHER, request.body()));
        saveMessageAttachments(message, request.attachmentsOrEmpty());
        question.answer();
        return adminDetail(question);
    }

    @Transactional
    public AdminQuestionDetail changeVisibility(UUID questionId, boolean publicVisible) {
        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
        question.changeVisibility(publicVisible);
        return adminDetail(question);
    }

    private QuestionDetail studentDetail(UUID meId, Question question) {
        List<QuestionAttachment> attachments = attachmentRepository.findAllByQuestionIdOrderBySortOrderAsc(question.getId());
        List<QuestionMessage> messages = messageRepository.findAllByQuestionIdAndDeletedAtIsNullOrderByCreatedAtAsc(question.getId());
        Map<UUID, List<QuestionAttachment>> messageAttachments = attachmentsByMessage(messages);
        return QuestionDetail.of(question, meId, attachments, messages, messageAttachments);
    }

    private AdminQuestionDetail adminDetail(Question question) {
        List<QuestionAttachment> attachments = attachmentRepository.findAllByQuestionIdOrderBySortOrderAsc(question.getId());
        List<QuestionMessage> messages = messageRepository.findAllByQuestionIdAndDeletedAtIsNullOrderByCreatedAtAsc(question.getId());
        Map<UUID, List<QuestionAttachment>> messageAttachments = attachmentsByMessage(messages);
        return AdminQuestionDetail.of(question, attachments, messages, messageAttachments);
    }

    private Map<UUID, List<QuestionAttachment>> attachmentsByMessage(List<QuestionMessage> messages) {
        List<UUID> messageIds = messages.stream().map(QuestionMessage::getId).toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findAllByMessageIdInOrderBySortOrderAsc(messageIds).stream()
                .collect(Collectors.groupingBy(a -> a.getMessage().getId()));
    }

    private List<QuestionAttachment> saveQuestionAttachments(Question question, List<AttachmentRequest> attachments) {
        return attachmentRepository.saveAll(IntStream.range(0, attachments.size())
                .mapToObj(i -> toAttachment(question, null, attachments.get(i), i))
                .toList());
    }

    private List<QuestionAttachment> saveMessageAttachments(QuestionMessage message, List<AttachmentRequest> attachments) {
        return attachmentRepository.saveAll(IntStream.range(0, attachments.size())
                .mapToObj(i -> toAttachment(null, message, attachments.get(i), i))
                .toList());
    }

    private QuestionAttachment toAttachment(Question question, QuestionMessage message, AttachmentRequest request, int sortOrder) {
        if (!fileStorage.exists(request.storageKey())) {
            throw new BusinessException(ErrorCode.FILE_NOT_UPLOADED, "업로드가 완료되지 않은 첨부파일이 있습니다.");
        }
        return new QuestionAttachment(question, message, request.storageKey(), request.mimeType(), request.byteSize(),
                request.width(), request.height(), sortOrder);
    }

    private void validateAttachments(List<AttachmentRequest> attachments) {
        if (attachments.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "첨부파일은 최대 5개까지 가능합니다.");
        }
        for (AttachmentRequest attachment : attachments) {
            if (!ALLOWED_MIME_TYPES.contains(attachment.mimeType())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 첨부 형식입니다.");
            }
        }
    }

    private void validateReferenceShape(QuestionCategory category, UUID examId, Integer itemNo, UUID wordDayId,
                                        UUID assignmentId, String textbook, Integer page) {
        if (category == QuestionCategory.LISTENING && (examId == null || itemNo == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "리스닝 질문은 시험과 문항 번호가 필요합니다.");
        }
        if (category == QuestionCategory.VOCAB && wordDayId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "단어 질문은 DAY가 필요합니다.");
        }
        if (category == QuestionCategory.HOMEWORK && assignmentId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "숙제 질문은 숙제가 필요합니다.");
        }
        if (category == QuestionCategory.TEXTBOOK && (trimToNull(textbook) == null || page == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "교재 질문은 교재명과 페이지가 필요합니다.");
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    private String encodeCursor(Instant createdAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private CursorParts decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorParts(null, null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new CursorParts(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "커서가 올바르지 않습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private record CursorParts(Instant createdAt, UUID id) {
        CursorParts orFirstDescending() {
            return createdAt == null ? new CursorParts(FIRST_DESC_CURSOR_CREATED_AT, FIRST_CURSOR_ID) : this;
        }

        CursorParts orFirstAscending() {
            return createdAt == null ? new CursorParts(FIRST_ASC_CURSOR_CREATED_AT, FIRST_CURSOR_ID) : this;
        }
    }
}
