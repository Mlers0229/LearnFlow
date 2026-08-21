package com.learnflow.repository;

import com.learnflow.entity.ResourceIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceIngestionRepository extends JpaRepository<ResourceIngestion, UUID> {
    List<ResourceIngestion> findByResourceIdOrderByCreatedAtDesc(Long resourceId);
}
