package com.learnflow.repository;

import com.learnflow.entity.ResourceChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceChunkRepository extends JpaRepository<ResourceChunk, UUID> {
    List<ResourceChunk> findByResourceIdAndContentHashIn(Long resourceId, Collection<String> hashes);
}
