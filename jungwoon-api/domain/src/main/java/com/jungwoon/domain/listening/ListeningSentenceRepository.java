package com.jungwoon.domain.listening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListeningSentenceRepository extends JpaRepository<ListeningSentence, Long> {

    List<ListeningSentence> findAllByItemIdOrderBySeqAsc(UUID itemId);

    /**
     * 오프라인 매니페스트용 일괄 조회.
     * 문항마다 부르면 한 회차에 17번 나간다.
     */
    List<ListeningSentence> findAllByItemIdInOrderByItemIdAscSeqAsc(List<UUID> itemIds);

    void deleteByItemId(UUID itemId);
}
