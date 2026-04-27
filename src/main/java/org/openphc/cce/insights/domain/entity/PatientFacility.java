package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_facility")
@IdClass(PatientFacilityId.class)
@Immutable
@Getter
public class PatientFacility {

    @Id
    @Column(name = "patient_id")
    private String patientId;

    @Id
    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "first_seen_at")
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    protected PatientFacility() {
    }
}
