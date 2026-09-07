package com.lmguard.entity;

import com.lmguard.entity.enums.CheckResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One inspector-recorded answer to a compliance-checklist line.
 *
 * <p>The checklist's fixed item set (code/title/guidance) is owned by the Flutter app, not by
 * this table - only the inspector's own {@code result}/{@code note} for each item is persisted
 * here, upserted by ({@code inspection}, {@code itemCode}).
 */
@Entity
@Table(name = "checklist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "guidance", columnDefinition = "TEXT")
    private String guidance;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    @Builder.Default
    private CheckResult result = CheckResult.PENDING;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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
