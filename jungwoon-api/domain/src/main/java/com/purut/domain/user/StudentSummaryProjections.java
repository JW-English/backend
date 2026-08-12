package com.purut.domain.user;

/**
 * 마이페이지 요약용 집계 결과.
 *
 * 화면에 숫자 몇 개를 띄우려고 목록을 통째로 읽어오면 안 된다.
 * 각각 집계 쿼리 한 번으로 끝낸다.
 */
public final class StudentSummaryProjections {

    private StudentSummaryProjections() {
    }

    /** 숙제 제출 현황 */
    public interface HomeworkCounts {
        long getTotal();

        long getSubmitted();

        long getReviewed();
    }

    /** 단어시험 성적 요약 */
    public interface QuizStats {
        long getAttemptCount();

        Double getAverageScore();

        Double getBestScore();
    }
}
