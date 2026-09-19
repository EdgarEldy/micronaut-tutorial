package com.edgareldy.micronauttutorial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One-shot password reset token. Only the SHA-256 hex of the raw token is stored; a used token is deleted.
 * The owner is a plain Long userId column (no JPA relation): the foreign key lives in the Flyway schema.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    /** Value of the NOT NULL type column: the only kind of reset token this application issues. */
    public static final String TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String token;

    @Column(nullable = false, length = 50)
    private String type = TYPE_PASSWORD_RESET;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    public PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String token, Instant expiryDate) {
        this.userId = userId;
        this.token = token;
        this.expiryDate = expiryDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    @Override
    public String toString() {
        return "PasswordResetToken{id=" + id + ", userId=" + userId + ", token=***}";
    }
}
