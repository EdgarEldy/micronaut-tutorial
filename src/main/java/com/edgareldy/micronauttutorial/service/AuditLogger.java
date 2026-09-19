package com.edgareldy.micronauttutorial.service;

/**
 * Writes the audit trail of RBAC mutations. Successes join the caller's transaction, refusals are written independently so they survive a rollback.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface AuditLogger {

    /** Records a successful mutation inside the caller's transaction (committed or rolled back with it). */
    void log(String action, String entityType, Long entityId, String details);

    /** Records a refused mutation in its own transaction, so the row is kept even if the caller rolls back. */
    void logRejected(String action, String entityType, Long entityId, String details);
}
