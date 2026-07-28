package com.jungwoon.api.vocabulary;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.vocabulary.dto.VocabularyDtos.DayDetail;
import com.jungwoon.api.vocabulary.dto.VocabularyDtos.DayListItem;
import com.jungwoon.api.vocabulary.dto.VocabularyDtos.WordItem;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import com.jungwoon.domain.vocabulary.DayWordCount;
import com.jungwoon.domain.vocabulary.QuizAttempt;
import com.jungwoon.domain.vocabulary.QuizAttemptRepository;
import com.jungwoon.domain.vocabulary.WordDay;
import com.jungwoon.domain.vocabulary.WordDayItem;
import com.jungwoon.domain.vocabulary.WordDayItemRepository;
import com.jungwoon.domain.vocabulary.WordDayRepository;
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
     * 학년별 DAY 목록.
     * 다른 학년도 열람할 수 있다 (기획 5.4) — 열람은 자유, 통계만 본인 학년 기준이다.
     */
    @Transactional(readOnly = true)
    public List<DayListItem> listDays(UserPrincipal me, Integer grade) {
        int targetGrade = grade != null ? grade : myGrade(me);

        List<WordDay> days = dayRepository.findOpenDays(targetGrade, LocalDate.now());
        if (days.isEmpty()) {
            return List.of();
        }

        List<UUID> dayIds = days.stream().map(WordDay::getId).toList();

        Map<UUID, Long> wordCounts = dayItemRepository.countByDays(dayIds).stream()
                .collect(Collectors.toMap(DayWordCount::dayId, DayWordCount::count));

        Map<UUID, List<QuizAttempt>> attempts = attemptRepository
                .findFinishedByDays(me.id(), dayIds).stream()
                .collect(Collectors.groupingBy(attempt -> attempt.getDay().getId()));

        return days.stream()
                .map(day -> DayListItem.of(
                        day,
                        wordCounts.getOrDefault(day.getId(), 0L),
                        attempts.getOrDefault(day.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(QuizAttempt::getStartedAt))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DayDetail getDay(UUID dayId) {
        WordDay day = dayRepository.findOpenDay(dayId, LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.DAY_NOT_OPENED));

        List<WordItem> words = dayItemRepository.findAllByDay(dayId).stream()
                .map(WordDayItem::getWord)
                .map(WordItem::of)
                .toList();

        return new DayDetail(day.getId(), day.getDayNo(), day.getTitle(), day.getScheduledDate(), words);
    }

    /**
     * 학년은 DB 에서 읽는다. 토큰 클레임은 온보딩 이전 값(null)일 수 있다.
     */
    private int myGrade(UserPrincipal me) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (user.getGrade() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "학년 정보가 없습니다. 프로필에서 학년을 먼저 설정해 주세요.");
        }
        return user.getGrade();
    }
}
