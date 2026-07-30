package com.jungwoon.api.vocabulary;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.vocabulary.dto.QuizDtos.AnswerRequest;
import com.jungwoon.api.vocabulary.dto.QuizDtos.AttemptResponse;
import com.jungwoon.api.vocabulary.dto.QuizDtos.ResultResponse;
import com.jungwoon.api.vocabulary.dto.QuizDtos.StartRequest;
import com.jungwoon.api.vocabulary.dto.QuizDtos.AttemptHistoryItem;
import com.jungwoon.api.vocabulary.dto.QuizDtos.WrongNoteItem;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.common.error.NotFoundException;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import com.jungwoon.domain.vocabulary.QuestionType;
import com.jungwoon.domain.vocabulary.QuizAnswer;
import com.jungwoon.domain.vocabulary.QuizAttempt;
import com.jungwoon.domain.vocabulary.QuizAttemptRepository;
import com.jungwoon.domain.vocabulary.Word;
import com.jungwoon.domain.vocabulary.WordDay;
import com.jungwoon.domain.vocabulary.WordDayItem;
import com.jungwoon.domain.vocabulary.WordDayItemRepository;
import com.jungwoon.domain.vocabulary.WordDayRepository;
import com.jungwoon.domain.vocabulary.WordRepository;
import com.jungwoon.domain.vocabulary.WrongNote;
import com.jungwoon.domain.vocabulary.WrongNoteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 출제·채점. <b>전부 서버에서 한다.</b>
 * 정답을 클라이언트에 내려보내면 앱 메모리 조작으로 만점이 나온다.
 */
@Service
public class QuizService {

    private static final int MIN_POOL_SIZE = 4;

    private final WordDayRepository dayRepository;
    private final WordDayItemRepository dayItemRepository;
    private final WordRepository wordRepository;
    private final QuizAttemptRepository attemptRepository;
    private final WrongNoteRepository wrongNoteRepository;
    private final QuizGenerationService generationService;
    private final UserRepository userRepository;

    public QuizService(WordDayRepository dayRepository,
                       WordDayItemRepository dayItemRepository,
                       WordRepository wordRepository,
                       QuizAttemptRepository attemptRepository,
                       WrongNoteRepository wrongNoteRepository,
                       QuizGenerationService generationService,
                       UserRepository userRepository) {
        this.dayRepository = dayRepository;
        this.dayItemRepository = dayItemRepository;
        this.wordRepository = wordRepository;
        this.attemptRepository = attemptRepository;
        this.wrongNoteRepository = wrongNoteRepository;
        this.generationService = generationService;
        this.userRepository = userRepository;
    }

    /** 응시 시작. 재응시는 무제한이므로 매번 새 attempt 를 만든다. */
    @Transactional
    public AttemptResponse start(UserPrincipal me, StartRequest request) {
        WordDay day = dayRepository.findOpenDay(request.dayId(), LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.DAY_NOT_OPENED));

        List<Word> pool = dayItemRepository.findAllByDay(day.getId()).stream()
                .map(WordDayItem::getWord)
                .toList();

        if (pool.size() < MIN_POOL_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "이 DAY 에는 시험을 만들 단어가 부족합니다 (최소 %d개).".formatted(MIN_POOL_SIZE));
        }

        // 기본은 DAY 의 단어 전부다. 일부만 내면 안 나온 단어를 건너뛴 채
        // 합격이 되어서, DAY 를 다 외웠는지 판정하는 시험이 되지 않는다
        int questionCount = request.questionCount() != null
                ? Math.min(request.questionCount(), pool.size())
                : pool.size();

        QuestionType questionType = request.questionType() != null
                ? request.questionType()
                : QuestionType.EN_TO_KO;

        User student = userRepository.getReferenceById(me.id());
        QuizAttempt attempt = attemptRepository.save(new QuizAttempt(student, day));

        List<QuizAnswer> answers =
                generationService.generate(attempt, pool, questionCount, questionType);
        answers.forEach(attempt::addAnswer);
        attemptRepository.flush();

        return AttemptResponse.of(attempt);
    }

    /** 중도 이탈 후 이어하기. */
    @Transactional(readOnly = true)
    public AttemptResponse getAttempt(UserPrincipal me, UUID attemptId) {
        QuizAttempt attempt = mine(me, attemptId);
        if (attempt.isFinished()) {
            throw new BusinessException(ErrorCode.ATTEMPT_ALREADY_FINISHED);
        }
        return AttemptResponse.of(attempt);
    }

    /**
     * 답 저장. 정오답은 알려주지 않는다 —
     * 즉시 알려주면 시험이 아니라 연습이 되고, 정답 탐색도 가능해진다.
     */
    @Transactional
    public void answer(UserPrincipal me, UUID attemptId, AnswerRequest request) {
        QuizAttempt attempt = mine(me, attemptId);
        if (attempt.isFinished()) {
            throw new BusinessException(ErrorCode.ATTEMPT_ALREADY_FINISHED);
        }

        QuizAnswer answer = attempt.getAnswers().stream()
                .filter(a -> a.getWord().getId().equals(request.wordId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("출제되지 않은 문항입니다."));

        if (request.selectedIndex() >= answer.getChoices().size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "보기 번호가 올바르지 않습니다.");
        }

        answer.select(request.selectedIndex());
    }

    /**
     * 제출·채점. 채점과 오답노트 갱신을 한 트랜잭션으로 묶는다.
     * 이미 끝난 응시는 거부한다 (중복 제출 방어).
     */
    @Transactional
    public ResultResponse submit(UserPrincipal me, UUID attemptId) {
        QuizAttempt attempt = mine(me, attemptId);
        if (attempt.isFinished()) {
            throw new BusinessException(ErrorCode.ATTEMPT_ALREADY_FINISHED);
        }

        int correctCount = 0;
        for (QuizAnswer answer : attempt.getAnswers()) {
            boolean correct = answer.grade();
            if (correct) {
                correctCount++;
            }
            updateWrongNote(me.id(), answer.getWord().getId(), correct);
        }

        attempt.finish(correctCount);

        // score 는 DB 생성 컬럼이라 UPDATE 가 나가야 값이 채워진다.
        // flush 없이 DTO 를 만들면 낡은 값(0점)이 응답에 실린다
        attemptRepository.flush();

        return ResultResponse.of(attempt);
    }

    @Transactional(readOnly = true)
    public ResultResponse getResult(UserPrincipal me, UUID attemptId) {
        QuizAttempt attempt = mine(me, attemptId);
        if (!attempt.isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "아직 제출하지 않은 시험입니다.");
        }
        return ResultResponse.of(attempt);
    }

    /**
     * 단어시험 응시 이력. 마이페이지에서 쓴다.
     *
     * 재응시가 무제한이라 쌓이므로 페이지 단위로 끊는다.
     */
    @Transactional(readOnly = true)
    public List<AttemptHistoryItem> history(UserPrincipal me, int page, int size) {
        return attemptRepository
                .findHistory(me.id(), PageRequest.of(page, Math.min(size, 100)))
                .stream()
                .map(a -> new AttemptHistoryItem(
                        a.getId(),
                        a.getDay().getId(),
                        a.getDay().getDayNo(),
                        a.getDay().getTitle(),
                        a.getTotalCount(),
                        a.getCorrectCount(),
                        a.getScore() == null ? 0 : a.getScore().doubleValue(),
                        a.isPassed(),
                        a.getFinishedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WrongNoteItem> wrongNotes(UserPrincipal me) {
        List<WrongNote> notes = wrongNoteRepository.findActive(me.id());
        if (notes.isEmpty()) {
            return List.of();
        }

        List<Long> wordIds = notes.stream().map(note -> note.getId().getWordId()).toList();
        var words = wordRepository.findAllById(wordIds).stream()
                .collect(java.util.stream.Collectors.toMap(Word::getId, word -> word));

        return notes.stream()
                .map(note -> {
                    Word word = words.get(note.getId().getWordId());
                    return new WrongNoteItem(
                            note.getId().getWordId(),
                            word == null ? "" : word.getHeadword(),
                            word == null ? "" : word.getMeaningKo(),
                            note.getWrongCount(),
                            note.getStreakCount(),
                            note.getLastWrongAt());
                })
                .toList();
    }

    /**
     * 오답노트 갱신.
     * 처음 맞힌 단어는 노트에 넣지 않는다 — 틀린 적이 있어야 복습 대상이다.
     */
    private void updateWrongNote(UUID studentId, Long wordId, boolean correct) {
        var id = new WrongNote.WrongNoteId(studentId, wordId);
        WrongNote note = wrongNoteRepository.findById(id).orElse(null);

        if (note == null) {
            if (!correct) {
                wrongNoteRepository.save(new WrongNote(studentId, wordId));
            }
            return;
        }

        if (correct) {
            note.markCorrect();
        } else {
            note.markWrong();
        }
    }

    private QuizAttempt mine(UserPrincipal me, UUID attemptId) {
        return attemptRepository.findMine(attemptId, me.id())
                .orElseThrow(() -> new NotFoundException("응시 기록을 찾을 수 없습니다."));
    }
}
