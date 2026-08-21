package com.learnflow.repository;

import com.learnflow.entity.ResourceIngestionChunk;
import com.learnflow.entity.ResourceIngestionChunkId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceIngestionChunkRepository extends JpaRepository<ResourceIngestionChunk, ResourceIngestionChunkId> {
    List<ResourceIngestionChunk> findByIngestionIdOrderByOrdinal(UUID ingestionId);
    void deleteByIngestionId(UUID ingestionId);
}
