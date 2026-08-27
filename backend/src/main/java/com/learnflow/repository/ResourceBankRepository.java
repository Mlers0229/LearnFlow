package com.learnflow.repository;

import com.learnflow.entity.ResourceBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceBankRepository extends JpaRepository<ResourceBank, Long> {

    List<ResourceBank> findByStatusOrderByCreatedAtDesc(String status);

    List<ResourceBank> findAllByOrderByCreatedAtDesc();

    List<ResourceBank> findAllByStatusNotOrderByCreatedAtDesc(String status);

    Optional<ResourceBank> findByIdAndStatusNot(Long id, String status);

    List<ResourceBank> findByUploaderUserIdOrderByCreatedAtDesc(Long uploaderUserId);

    List<ResourceBank> findByUploaderUserIdAndStatusNotOrderByCreatedAtDesc(Long uploaderUserId, String status);

    List<ResourceBank> findByUploaderUsernameOrderByCreatedAtDesc(String uploaderUsername);

    List<ResourceBank> findByUploaderUsernameAndStatusNotOrderByCreatedAtDesc(String uploaderUsername, String status);

    boolean existsByTitleIgnoreCaseAndUrl(String title, String url);
}


