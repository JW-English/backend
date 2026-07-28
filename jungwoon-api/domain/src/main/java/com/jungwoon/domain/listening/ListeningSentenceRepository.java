package com.jungwoon.domain.listening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListeningSentenceRepository extends JpaRepository<ListeningSentence, Long> {

    List<ListeningSentence> findAllByItemIdOrderBySeqAsc(UUID itemId);

    void deleteByItemId(UUID itemId);
}
