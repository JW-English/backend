package com.purut.api.listening.dto;

import com.purut.domain.listening.Exam;
import com.purut.domain.listening.ExamType;
import com.purut.domain.listening.ListeningItem;
import com.purut.domain.listening.ListeningSentence;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ListeningDtos {

    private ListeningDtos() {
    }

    public record ExamListItem(
            UUID id,
            int year,
            ExamType examType,
            String examTypeLabel,
            int grade,
            String title,
            int itemCount,
            int completedCount
    ) {
        public static ExamListItem of(Exam exam, int itemCount, int completedCount) {
            return new ExamListItem(exam.getId(), exam.getYear(), exam.getExamType(),
                    exam.getExamType().label(), exam.getGrade(), exam.getTitle(),
                    itemCount, completedCount);
        }
    }

    public record ItemListItem(
            UUID id,
            int itemNo,
            String itemType,
            String questionText,
            Integer durationMs,
            boolean completed,
            int lastPositionMs
    ) {
        public static ItemListItem of(ListeningItem item, Integer lastPositionMs, boolean completed) {
            return new ItemListItem(item.getId(), item.getItemNo(), item.getItemType(),
                    item.getQuestionText(), item.getDurationMs(), completed,
                    lastPositionMs == null ? 0 : lastPositionMs);
        }
    }

    /**
     * 문장 1개. start/end 로 앱이 단일 음원 안에서 seek 한다.
     * 문장을 누르면 startMs 로 이동해 재생하는 구조다.
     */
    public record SentenceItem(
            long id,
            int seq,
            String speaker,
            String textEn,
            String textKo,
            int startMs,
            int endMs
    ) {
        public static SentenceItem of(ListeningSentence sentence) {
            return new SentenceItem(sentence.getId(), sentence.getSeq(), sentence.getSpeaker(),
                    sentence.getTextEn(), sentence.getTextKo(),
                    sentence.getStartMs(), sentence.getEndMs());
        }
    }

    /**
     * 전체 듣기 한 트랙.
     *
     * 16·17 번처럼 여러 문항이 한 음원을 공유하면 트랙 하나로 합쳐 내려간다.
     * 그대로 나열하면 같은 파일이 두 번 재생된다.
     */
    public record PlaylistTrack(
            /** INTRO | ITEM */
            String kind,
            /** INTRO 면 null */
            UUID itemId,
            /** 화면에 쓸 이름. "안내 방송", "1번", "16-17번" */
            String label,
            String audioUrl,
            Integer durationMs
    ) {
    }

    public record Playlist(
            UUID examId,
            String examLabel,
            List<PlaylistTrack> tracks
    ) {
    }

    /**
     * 오프라인 다운로드용. 회차 하나를 기기에 담는 데 필요한 것을 한 번에 준다.
     *
     * 문항 상세를 17번 부르면 요청도 많고 중간에 실패하면 반쯤 받은 상태가 된다.
     */
    public record DownloadItem(
            UUID id,
            int itemNo,
            String questionText,
            String audioUrl,
            /** 기기에 저장할 때 쓸 이름. presigned URL 은 만료되므로 키를 따로 준다 */
            String audioKey,
            Integer durationMs,
            List<SentenceItem> sentences
    ) {
    }

    public record DownloadManifest(
            UUID examId,
            String examLabel,
            /** 안내 방송. 없는 회차면 null */
            String introUrl,
            String introKey,
            List<DownloadItem> items
    ) {
    }

    public record ItemDetail(
            UUID id,
            int itemNo,
            String itemType,
            String questionText,
            /** "2026학년도 수능" — 문항 화면에서 어느 회차인지 보여주려고 함께 내려준다 */
            String examLabel,
            /** 만료형 재생 URL. 키가 아니라 URL 로 내려준다 */
            String audioUrl,
            Integer durationMs,
            int lastPositionMs,
            List<SentenceItem> sentences
    ) {
    }

    public record ProgressRequest(
            @NotNull @Min(0) Integer lastPositionMs,
            boolean completed
    ) {
    }
}
