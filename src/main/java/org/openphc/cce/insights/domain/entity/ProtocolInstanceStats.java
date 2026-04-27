package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "protocol_instance_stats")
@Immutable
@Getter
public class ProtocolInstanceStats {

    @Id
    @Column(name = "protocol_instance_id")
    private UUID protocolInstanceId;

    @Column(name = "total_steps")
    private int totalSteps;

    @Column(name = "completed_steps")
    private int completedSteps;

    @Column(name = "early_steps")
    private int earlySteps;

    @Column(name = "on_time_steps")
    private int onTimeSteps;

    @Column(name = "late_steps")
    private int lateSteps;

    @Column(name = "overdue_steps")
    private int overdueSteps;

    @Column(name = "missed_steps")
    private int missedSteps;

    @Column(name = "skipped_steps")
    private int skippedSteps;

    @Column(name = "pending_steps")
    private int pendingSteps;

    @Column(name = "total_deviations")
    private int totalDeviations;

    @Column(name = "overdue_deviations")
    private int overdueDeviations;

    @Column(name = "missed_deviations")
    private int missedDeviations;

    @Column(name = "order_violation_deviations")
    private int orderViolationDeviations;

    @Column(name = "last_updated")
    private OffsetDateTime lastUpdated;

    protected ProtocolInstanceStats() {
    }
}
