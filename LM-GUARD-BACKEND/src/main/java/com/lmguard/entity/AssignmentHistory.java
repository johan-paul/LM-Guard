package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One audit-trail row per inspection assignment or reassignment.
 *
 * <p>{@code createdAt} (inherited from {@link BaseEntity}) doubles as "assigned at" - a
 * dedicated column would only ever hold the same value, since a history row is written at
 * the moment the assignment happens and is never edited afterwards.
 */
@Entity
@Table(name = "assignment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    /** Null for the first assignment of a newly created inspection. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_inspector_id")
    private User fromInspector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_inspector_id", nullable = false)
    private User toInspector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_zone_id")
    private Zone fromZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_zone_id")
    private Zone toZone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
}
