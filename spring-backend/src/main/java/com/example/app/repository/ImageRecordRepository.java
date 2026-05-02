package com.example.app.repository;

import java.util.List;
import java.util.Optional;

import com.example.app.model.ImageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRecordRepository extends JpaRepository<ImageRecord, Long> {

    List<ImageRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ImageRecord> findByIdAndUserId(Long id, Long userId);
}
