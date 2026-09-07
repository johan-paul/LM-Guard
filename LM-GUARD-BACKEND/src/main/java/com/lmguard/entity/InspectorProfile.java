package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Directory profile for an inspecting officer - the fields the admin console's Inspector
 * Management screen needs that do not belong on {@link User} (which is auth-only: name,
 * email, password, role, enabled).
 *
 * <p>Kept as a satellite table rather than new columns on {@code users} so authentication
 * code never has to know about zones, ranks or badge numbers.
 */
@Entity
@Table(name = "inspector_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Human-readable badge number shown throughout the admin console, e.g. "LM-INS-101". */
    @Column(name = "officer_code", nullable = false, length = 20, unique = true)
    private String officerCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "rank", nullable = false, length = 50)
    @Builder.Default
    private String rank = "Inspecting Officer";

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    /** Updated on successful login. Null means the officer has never signed in. */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        if (joinedOn == null) {
            joinedOn = LocalDate.now();
        }
        if (updatedAt == null) {
            updatedAt = getCreatedAt();
        }
    }
}
