package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared identity and creation timestamp for every persisted entity.
 *
 * <p>UUIDs are assigned in Java on {@code @PrePersist} rather than by the database.
 * That keeps the schema free of a {@code pgcrypto} dependency, lets the same entities
 * run unchanged against an in-memory database in tests, and leaves {@code id} null
 * until insert so Spring Data's {@code isNew()} check still routes {@code save()}
 * through {@code persist()} instead of an unnecessary {@code merge()}.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Identity is the primary key alone. {@code getClass()} is deliberately not compared:
     * a Hibernate lazy proxy is a subclass, and comparing classes would make an entity
     * unequal to its own proxy.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.getId());
    }

    /**
     * Constant hash code. The id is null before insert and non-null after, so hashing on it
     * would change an entity's bucket mid-collection. A constant keeps hash-based collections
     * correct (equals still separates entities) at the cost of linear lookup, which is
     * irrelevant for the small collections this application holds in memory.
     */
    @Override
    public int hashCode() {
        return 31;
    }
}
