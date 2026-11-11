package com.truckconnect.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId);
}
