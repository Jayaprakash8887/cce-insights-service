package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Table(name = "facility_stats")
@Immutable
@Getter
public class FacilityStats {

    @Id
    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(name = "total_events")
    private long totalEvents;

    @Column(name = "total_patients")
    private long totalPatients;

    @Column(name = "total_enrollments")
    private long totalEnrollments;

    @Column(name = "total_deviations")
    private long totalDeviations;

    @Column(name = "overdue_count")
    private long overdueCount;

    @Column(name = "missed_count")
    private long missedCount;

    @Column(name = "completed_steps")
    private long completedSteps;

    @Column(name = "total_steps")
    private long totalSteps;

    @Column(name = "last_updated")
    private OffsetDateTime lastUpdated;

    protected FacilityStats() {
    }
}
