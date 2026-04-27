package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.PatientFacility;
import org.openphc.cce.insights.domain.entity.PatientFacilityId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatientFacilityRepository extends ReadOnlyRepository<PatientFacility, PatientFacilityId> {

    List<PatientFacility> findByFacilityId(String facilityId);

    @Query("SELECT pf.facilityId, pf.patientId FROM PatientFacility pf")
    List<Object[]> findAllFacilityPatientMapping();

    @Query("SELECT pf.facilityId, COUNT(pf) FROM PatientFacility pf GROUP BY pf.facilityId")
    List<Object[]> countPatientsByFacility();
}
