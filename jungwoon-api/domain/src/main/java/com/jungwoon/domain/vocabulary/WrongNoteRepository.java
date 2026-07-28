package com.jungwoon.domain.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WrongNoteRepository extends JpaRepository<WrongNote, WrongNote.WrongNoteId> {

    @Query("""
            select n from WrongNote n
            where n.id.studentId = :studentId and n.masteredAt is null
            order by n.lastWrongAt desc
            """)
    List<WrongNote> findActive(UUID studentId);
}
