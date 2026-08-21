package com.learnflow.repository;

import com.learnflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndEmailIgnoreCase(String username, String email);

    boolean existsByUsername(String username);
}



