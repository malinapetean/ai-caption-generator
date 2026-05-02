package com.example.app.repository;

import java.util.List;
import java.util.Optional;

import com.example.app.model.Caption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptionRepository extends JpaRepository<Caption, Long> {

    List<Caption> findByImageUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Caption> findByIdAndImageUserId(Long id, Long userId);

    List<Caption> findByImageId(Long imageId);
}
