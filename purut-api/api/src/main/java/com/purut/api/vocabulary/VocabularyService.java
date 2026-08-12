package com.purut.api.vocabulary;

import com.purut.api.auth.UserPrincipal;
import com.purut.api.vocabulary.dto.VocabularyDtos.DayDetail;
import com.purut.api.vocabulary.dto.VocabularyDtos.DayListItem;
import com.purut.api.vocabulary.dto.VocabularyDtos.WordItem;
import com.purut.common.error.BusinessException;
import com.purut.common.error.ErrorCode;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.domain.vocabulary.DayWordCount;
import com.purut.domain.vocabulary.QuizAttempt;
import com.purut.domain.vocabulary.QuizAttemptRepository;
import com.purut.domain.vocabulary.VocabLevel;
import com.purut.domain.vocabulary.WordDay;
import com.purut.domain.vocabulary.WordDayItem;
import com.purut.domain.vocabulary.WordDayItemRepository;
import com.purut.domain.vocabulary.WordDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 학생용 단어장. 열린 DAY 만 보인다. */
@Service
public class VocabularyService {

    private final WordDayRepository dayRepository;
    private final WordDayItemRepository dayItemRepository;
    private final QuizAttemptRepository attemptRepository;
    private final UserRepository userRepository;

    public VocabularyService(WordDayRepository dayRepository,
                             WordDayItemRepository dayItemRepository,
                             QuizAttemptRepository attemptRepository,
                             UserRepository userRepository) {
        this.dayRepository = dayRepository;
        this.dayItemRepository = dayItemRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
    }

    /**
     * 레벨별 DAY 목록.
     * 다른 레벨도 열람할 수 있다 — 위아래를 둘러보는 건 막지 않는다.
     */
    @Transactional(readOnly = true)
    public List<DayListItem> listDays(UserPrincipal me, VocabLevel level) {
        VocabLevel target = level != null ? level : myLevel(me);

        List<WordDay> days = dayRepository.findOpenDays(target, LocalDate.now());
        if (days.isEmpty()) {
            return List.of();
        }

        List<UUID> dayIds = days.stream().map(WordDay::getId).toList();

        Map<UUID, Long> wordCounts = dayItemRepository.countByDays(dayIds).stream()
                .collect(Collectors.toMap(DayWordCount::dayId, DayWordCount::count));

        Map<UUID, List<QuizAttempt>> attempts = attemptRepository
                .findFinishedByDays(me.id(), dayIds).stream()
                .collect(Collectors.groupingBy(attempt -> attempt.getDay().getId()));

        // 나갔다 돌아온 응시 — DAY 당 가장 최근 것 하나만 이어 풀게 한다
        Map<UUID, UUID> inProgress = attemptRepository.findInProgressByDays(me.id(), dayIds).stream()
                .collect(Collectors.toMap(
                        attempt -> attempt.getDay().getId(),
                        QuizAttempt::getId,
                        (latest, older) -> latest));

        return days.stream()
                .map(day -> DayListItem.of(
                        day,
                        wordCounts.getOrDefault(day.getId(), 0L),
                        attempts.getOrDefault(day.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(QuizAttempt::getStartedAt))
                                .toList(),
                        inProgress.get(day.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DayDetail getDay(UserPrincipal me, UUID dayId) {
        WordDay day = dayRepository.findOpenDay(dayId, LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.DAY_NOT_OPENED));

        List<WordItem> words = dayItemRepository.findAllByDay(dayId).stream()
                .map(WordDayItem::getWord)
                .map(WordItem::of)
                .toList();

        UUID inProgressAttemptId = attemptRepository
                .findInProgressByDays(me.id(), List.of(dayId)).stream()
                .findFirst()
                .map(QuizAttempt::getId)
                .orElse(null);

        return new DayDetail(day.getId(), day.getDayNo(), day.getTitle(), day.getScheduledDate(),
                words, inProgressAttemptId);
    }

    /**
     * 어휘 레벨은 DB 에서 읽는다. 토큰 클레임은 온보딩 이전 값(null)일 수 있다.
     *
     * 레벨이 없으면 학교 학년으로 추정한다 — 온보딩만 마친 학생도 바로 쓸 수 있어야 한다.
     * 선생님이나 학생이 설정에서 조정하면 그 값이 우선한다.
     */
    private VocabLevel myLevel(UserPrincipal me) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (user.getVocabLevel() != null) {
            return user.getVocabLevel();
        }

        Integer grade = user.getGrade();
        if (grade == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "학년 정보가 없습니다. 프로필에서 학년을 먼저 설정해 주세요.");
        }
        return switch (grade) {
            case 1 -> VocabLevel.BEGINNER;
            case 3 -> VocabLevel.ADVANCED;
            default -> VocabLevel.INTERMEDIATE;
        };
    }
}
