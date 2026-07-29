package com.jungwoon.api.listening;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.listening.dto.ListeningDtos.ExamListItem;
import com.jungwoon.api.listening.dto.ListeningDtos.ItemDetail;
import com.jungwoon.api.listening.dto.ListeningDtos.ItemListItem;
import com.jungwoon.api.listening.dto.ListeningDtos.Playlist;
import com.jungwoon.api.listening.dto.ListeningDtos.PlaylistTrack;
import com.jungwoon.api.listening.dto.ListeningDtos.ProgressRequest;
import com.jungwoon.api.listening.dto.ListeningDtos.SentenceItem;
import com.jungwoon.common.error.NotFoundException;
import com.jungwoon.domain.listening.Exam;
import com.jungwoon.domain.listening.ExamCompletedCount;
import com.jungwoon.domain.listening.ExamItemCount;
import com.jungwoon.domain.listening.ExamRepository;
import com.jungwoon.domain.listening.ListeningItem;
import com.jungwoon.domain.listening.ListeningItemRepository;
import com.jungwoon.domain.listening.ListeningProgress;
import com.jungwoon.domain.listening.ListeningProgressRepository;
import com.jungwoon.domain.listening.ListeningSentenceRepository;
import com.jungwoon.infra.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 리스닝 조회.
 *
 * 기출 음원은 전체 공개다 (기획 4.7 접근 규칙) — 학생별 소유권 개념이 없다.
 * 대신 진도(ListeningProgress)는 본인 것만 다룬다.
 */
@Service
public class ListeningService {

    private final ExamRepository examRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningSentenceRepository sentenceRepository;
    private final ListeningProgressRepository progressRepository;
    private final FileStorage fileStorage;

    public ListeningService(ExamRepository examRepository,
                            ListeningItemRepository itemRepository,
                            ListeningSentenceRepository sentenceRepository,
                            ListeningProgressRepository progressRepository,
                            FileStorage fileStorage) {
        this.examRepository = examRepository;
        this.itemRepository = itemRepository;
        this.sentenceRepository = sentenceRepository;
        this.progressRepository = progressRepository;
        this.fileStorage = fileStorage;
    }

    /**
     * 시험 목록.
     *
     * 시험마다 문항·진도를 조회하면 26개 시험에서 요청당 50번 넘는 쿼리가 나간다.
     * 문항 수와 완료 수를 각각 한 번에 집계해 총 3번으로 끝낸다.
     */
    @Transactional(readOnly = true)
    public List<ExamListItem> listExams(UserPrincipal me, Integer year) {
        List<Exam> exams = year != null
                ? examRepository.findAllByYearOrderByExamTypeAsc(year)
                : examRepository.findAllByOrderByYearDescExamTypeAsc();

        if (exams.isEmpty()) {
            return List.of();
        }

        List<UUID> examIds = exams.stream().map(Exam::getId).toList();

        Map<UUID, Long> itemCounts = itemRepository.countByExams(examIds).stream()
                .collect(Collectors.toMap(ExamItemCount::examId, ExamItemCount::count));

        Map<UUID, Long> completedCounts = progressRepository
                .countCompletedByExams(me.id(), examIds).stream()
                .collect(Collectors.toMap(ExamCompletedCount::examId, ExamCompletedCount::count));

        return exams.stream()
                .map(exam -> ExamListItem.of(
                        exam,
                        itemCounts.getOrDefault(exam.getId(), 0L).intValue(),
                        completedCounts.getOrDefault(exam.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemListItem> listItems(UserPrincipal me, UUID examId) {
        List<ListeningItem> items = itemRepository.findAllByExamIdOrderByItemNoAsc(examId);
        if (items.isEmpty()) {
            return List.of();
        }

        Map<UUID, ListeningProgress> progress = progressOf(me, items);

        return items.stream()
                .map(item -> {
                    ListeningProgress p = progress.get(item.getId());
                    return ItemListItem.of(item,
                            p == null ? null : p.getLastPositionMs(),
                            p != null && p.getCompletedAt() != null);
                })
                .toList();
    }

    /**
     * 전체 듣기 재생 목록. 안내 방송 → 1번 → … 순서다.
     *
     * 문항마다 상세를 부르면 요청이 17번 나가므로 한 번에 내려준다.
     */
    @Transactional(readOnly = true)
    public Playlist getPlaylist(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("시험을 찾을 수 없습니다."));

        List<PlaylistTrack> tracks = new ArrayList<>();

        // 안내 방송은 회차에 하나뿐이라 exams.audio_key 에 있다. 없는 회차는 건너뛴다
        String introKey = exam.getAudioKey();
        if (introKey != null && !introKey.isBlank()) {
            tracks.add(new PlaylistTrack(
                    "INTRO", null, "안내 방송", fileStorage.presignDownload(introKey), null));
        }

        // 16·17 번은 한 파일을 공유한다. 그대로 넣으면 같은 음원이 두 번 재생된다
        String lastKey = null;
        for (ListeningItem item : itemRepository.findAllByExamIdOrderByItemNoAsc(examId)) {
            if (item.getAudioKey().equals(lastKey)) {
                PlaylistTrack merged = tracks.removeLast();
                tracks.add(new PlaylistTrack(
                        merged.kind(), merged.itemId(),
                        mergeLabel(merged.label(), item.getItemNo()),
                        merged.audioUrl(), merged.durationMs()));
                continue;
            }

            lastKey = item.getAudioKey();
            tracks.add(new PlaylistTrack(
                    "ITEM", item.getId(), item.getItemNo() + "번",
                    fileStorage.presignDownload(item.getAudioKey()), item.getDurationMs()));
        }

        return new Playlist(
                exam.getId(),
                exam.getYear() + "학년도 " + exam.getExamType().label(),
                tracks);
    }

    /** "16번" + 17 → "16-17번" · "16-17번" + 18 → "16-18번" */
    private static String mergeLabel(String label, int itemNo) {
        String head = label.replace("번", "").split("-")[0];
        return head + "-" + itemNo + "번";
    }

    @Transactional(readOnly = true)
    public ItemDetail getItem(UserPrincipal me, UUID itemId) {
        ListeningItem item = itemRepository.findWithExam(itemId)
                .orElseThrow(() -> new NotFoundException("문항을 찾을 수 없습니다."));

        List<SentenceItem> sentences = sentenceRepository.findAllByItemIdOrderBySeqAsc(itemId).stream()
                .map(SentenceItem::of)
                .toList();

        ListeningProgress progress = progressRepository
                .findById(new ListeningProgress.ListeningProgressId(me.id(), itemId))
                .orElse(null);

        // findWithExam 으로 이미 fetch join 돼 있어 추가 쿼리가 나가지 않는다
        Exam exam = item.getExam();

        return new ItemDetail(
                item.getId(),
                item.getItemNo(),
                item.getItemType(),
                item.getQuestionText(),
                exam.getYear() + "학년도 " + exam.getExamType().label(),
                // 음원 키가 아니라 만료형 URL 을 준다. 비공개 버킷이라 URL 없이는 못 받는다
                fileStorage.presignDownload(item.getAudioKey()),
                item.getDurationMs(),
                progress == null ? 0 : progress.getLastPositionMs(),
                sentences);
    }

    /** 이어듣기 위치 저장. 자주 호출되므로 가볍게 유지한다. */
    @Transactional
    public void saveProgress(UserPrincipal me, UUID itemId, ProgressRequest request) {
        if (!itemRepository.existsById(itemId)) {
            throw new NotFoundException("문항을 찾을 수 없습니다.");
        }

        var id = new ListeningProgress.ListeningProgressId(me.id(), itemId);
        ListeningProgress progress = progressRepository.findById(id)
                .orElseGet(() -> progressRepository.save(new ListeningProgress(me.id(), itemId)));

        progress.record(request.lastPositionMs(), request.completed());
    }

    private Map<UUID, ListeningProgress> progressOf(UserPrincipal me, List<ListeningItem> items) {
        List<UUID> itemIds = items.stream().map(ListeningItem::getId).toList();
        return progressRepository.findAllByStudentAndItems(me.id(), itemIds).stream()
                .collect(Collectors.toMap(p -> p.getId().getItemId(), Function.identity()));
    }
}
