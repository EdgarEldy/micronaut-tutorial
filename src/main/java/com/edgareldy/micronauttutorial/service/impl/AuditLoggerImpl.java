package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.entity.AuditLog;
import com.edgareldy.micronauttutorial.repository.AuditLogRepository;
import com.edgareldy.micronauttutorial.service.AuditLogger;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Default AuditLogger. The actor is the authenticated user id (the JWT subject), or null when there is none.
 * Rejections are prefixed with REJECTED_ so the two outcomes can be told apart.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
public class AuditLoggerImpl implements AuditLogger {

    /** Prefix of the action column for a refused operation. */
    public static final String REJECTED_PREFIX = "REJECTED_";

    private final AuditLogRepository auditLogs;
    private final SecurityService securityService;

    public AuditLoggerImpl(AuditLogRepository auditLogs, SecurityService securityService) {
        this.auditLogs = auditLogs;
        this.securityService = securityService;
    }

    // Default propagation (REQUIRED): the insert joins the transaction of the calling service method, so
    // the audit row and the business change commit or roll back together.
    @Override
    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        auditLogs.save(new AuditLog(currentActorId(), action, entityType, entityId, details));
    }

    // REQUIRES_NEW suspends the caller's transaction and runs in a brand new one that commits on its own
    // when this method returns. The caller then throws and rolls back its business change, but the
    // refusal row is already committed. Works because this is another bean: the call goes through the
    // compile-time transactional interceptor (a call inside the same class would bypass it).
    @Override
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void logRejected(String action, String entityType, Long entityId, String details) {
        auditLogs.save(new AuditLog(currentActorId(), REJECTED_PREFIX + action, entityType, entityId, details));
    }

    private Long currentActorId() {
        return securityService.getAuthentication().map(Authentication::getName).map(name -> {
            try {
                return Long.valueOf(name);
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }
}
