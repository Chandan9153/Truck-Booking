package com.truckconnect.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(Role role);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
