package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A Legal Metrology zone/jurisdiction that inspectors are posted to and inspections belong to. */
@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150, unique = true)
    private String name;

    @Column(name = "code", length = 20, unique = true)
    private String code;

    @Column(name = "office", length = 255)
    private String office;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        if (updatedAt == null) {
            updatedAt = getCreatedAt();
        }
    }
}
