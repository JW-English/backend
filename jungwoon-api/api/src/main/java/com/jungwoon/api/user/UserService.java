package com.jungwoon.api.user;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.user.dto.MeDtos.HomeworkSummary;
import com.jungwoon.api.user.dto.MeDtos.PasswordChangeRequest;
import com.jungwoon.api.user.dto.MeDtos.Summary;
import com.jungwoon.api.user.dto.MeDtos.VocabularySummary;
import com.jungwoon.api.user.dto.OnboardingRequest;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.domain.homework.AssignmentRepository;
import com.jungwoon.domain.user.StudentSummaryProjections.HomeworkCounts;
import com.jungwoon.domain.user.StudentSummaryProjections.QuizStats;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import com.jungwoon.domain.vocabulary.QuizAttemptRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       AssignmentRepository assignmentRepository,
                       QuizAttemptRepository quizAttemptRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 온보딩. 다시 호출하면 프로필 수정으로 동작한다 —
     * 학년은 진급하면 바뀌므로 한 번만 되게 막지 않는다.
     */
    @Transactional
    public User completeOnboarding(UserPrincipal me, OnboardingRequest request) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        user.completeOnboarding(request.name(), request.grade(), request.school());
        return user;
    }

    /**
     * 마이페이지 요약.
     *
     * 숙제·단어시험 각각 집계 쿼리 한 번씩, 총 2번으로 끝낸다.
     * 목록을 읽어 세면 숙제가 늘수록 느려진다.
     */
    @Transactional(readOnly = true)
    public Summary summary(UserPrincipal me) {
        HomeworkCounts homework = assignmentRepository.summarizeForStudent(me.id());
        QuizStats quiz = quizAttemptRepository.summarizeForStudent(me.id());

        long total = homework.getTotal();
        long submitted = homework.getSubmitted();

        return new Summary(
                new HomeworkSummary(total, submitted, homework.getReviewed(),
                        Math.max(0, total - submitted)),
                new VocabularySummary(quiz.getAttemptCount(),
                        round1(quiz.getAverageScore()), round1(quiz.getBestScore())));
    }

    /** 소수점이 길게 붙으면 화면에서 지저분하다 */
    private static Double round1(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    /**
     * 비밀번호 변경.
     *
     * 현재 비밀번호를 반드시 확인한다 — 액세스 토큰만으로 바꾸게 두면
     * 토큰이 유출됐을 때 계정을 통째로 뺏긴다.
     */
    @Transactional
    public void changePassword(UserPrincipal me, PasswordChangeRequest request) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 회원탈퇴. 앱스토어 심사가 앱 내 계정 삭제 경로를 요구한다.
     *
     * 소프트 삭제다 — status 만 WITHDRAWN 으로 바꾼다. 로그인·소셜로그인·토큰 갱신이
     * 모두 isActive() 를 확인하므로 이후 세션을 새로 얻을 수 없다.
     * 이미 발급된 액세스 토큰은 만료(30분)까지 살아 있다.
     */
    @Transactional
    public void withdraw(UserPrincipal me) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        user.withdraw();
    }
}
