package com.learnflow.repository;

import com.learnflow.entity.AsyncTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsyncTaskRepository extends JpaRepository<AsyncTask, UUID> {

    Optional<AsyncTask> findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
            Long ownerUserId,
            String taskType,
            String idempotencyKey
    );

    Optional<AsyncTask> findByIdAndOwnerUserId(UUID id, Long ownerUserId);

    List<AsyncTask> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
