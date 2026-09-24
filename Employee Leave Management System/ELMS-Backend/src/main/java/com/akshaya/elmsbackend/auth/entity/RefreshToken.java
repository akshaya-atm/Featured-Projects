package com.akshaya.elmsbackend.auth.entity;

import com.akshaya.elmsbackend.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_ref_id", nullable = false)
    private Employee employee;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
        // Required by JPA.
    }

    public RefreshToken(
            Employee employee,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt) {

        this.employee = employee;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getReplacedByTokenId() {
        return replacedByTokenId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setReplacedByTokenId(Long replacedByTokenId) {
        this.replacedByTokenId = replacedByTokenId;
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}