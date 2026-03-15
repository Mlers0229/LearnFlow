package com.learnflow.repository;

import com.learnflow.entity.ResourceBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceBankRepository extends JpaRepository<ResourceBank, Long> {

    List<ResourceBank> findByStatusOrderByCreatedAtDesc(String status);

    List<ResourceBank> findAllByOrderByCreatedAtDesc();

    List<ResourceBank> findByUploaderUserIdOrderByCreatedAtDesc(Long uploaderUserId);

    List<ResourceBank> findByUploaderUsernameOrderByCreatedAtDesc(String uploaderUsername);

    boolean existsByTitleIgnoreCaseAndUrl(String title, String url);
}


