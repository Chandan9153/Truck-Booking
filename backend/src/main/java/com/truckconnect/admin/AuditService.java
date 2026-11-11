package com.truckconnect.admin;

import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User actor, String action, String targetType, Long targetId) {
        auditLogRepository.save(new AuditLog(actor, action, targetType, targetId));
    }
}
