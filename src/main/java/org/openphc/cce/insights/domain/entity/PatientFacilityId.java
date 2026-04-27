package org.openphc.cce.insights.domain.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PatientFacilityId implements Serializable {
    private String patientId;
    private String facilityId;
}
