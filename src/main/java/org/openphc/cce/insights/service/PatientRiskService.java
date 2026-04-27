package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.EventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.AtRiskHotspotDto;
import org.openphc.cce.insights.web.dto.RepeatDeviationPatientDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientRiskService {

    private final DeviationRepository deviationRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final EventLogRepository eventLogRepository;

    @Cacheable(value = "analytics", key = "'risk-hotspots-' + #startDate + '-' + #endDate")
    public List<AtRiskHotspotDto> getAtRiskHotspots(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Single query: get patient risk status grouped by facility
        List<Object[]> riskRows = stepInstanceRepository.findPatientRiskByFacility();

        // Aggregate per facility
        Map<String, long[]> facilityCounts = new LinkedHashMap<>(); // [onTrack, atRisk, nonCompliant]
        for (Object[] row : riskRows) {
            String facilityId = (String) row[0];
            boolean hasMissed = (Boolean) row[2];
            boolean hasOverdue = (Boolean) row[3];
            long[] counts = facilityCounts.computeIfAbsent(facilityId, k -> new long[3]);
            if (hasMissed) counts[2]++;
            else if (hasOverdue) counts[1]++;
            else counts[0]++;
        }

        // Build facility name lookup
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : eventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        return facilityCounts.entrySet().stream().map(entry -> {
            String facilityId = entry.getKey();
            long[] c = entry.getValue();
            long onTrack = c[0], atRisk = c[1], nonCompliant = c[2];
            long totalPatients = onTrack + atRisk + nonCompliant;

            return AtRiskHotspotDto.builder()
                    .facilityId(facilityId)
                    .facilityName(facilityNameMap.getOrDefault(facilityId, facilityId))
                    .totalPatients(totalPatients)
                    .onTrack(AtRiskHotspotDto.CategoryCount.builder()
                            .count(onTrack)
                            .percentage(totalPatients > 0 ? Math.round((double) onTrack / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .atRisk(AtRiskHotspotDto.CategoryCount.builder()
                            .count(atRisk)
                            .percentage(totalPatients > 0 ? Math.round((double) atRisk / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .nonCompliant(AtRiskHotspotDto.CategoryCount.builder()
                            .count(nonCompliant)
                            .percentage(totalPatients > 0 ? Math.round((double) nonCompliant / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "analytics", key = "'repeat-deviations-' + #minDeviations + '-' + #startDate + '-' + #endDate")
    public List<RepeatDeviationPatientDto> getRepeatDeviationPatients(int minDeviations,
                                                                       OffsetDateTime startDate,
                                                                       OffsetDateTime endDate) {
        List<Object[]> rows = deviationRepository.findRepeatDeviationPatients(
                minDeviations, null, startDate, endDate);

        return rows.stream().map(row -> RepeatDeviationPatientDto.builder()
                .patientId((String) row[0])
                .totalDeviations(((Number) row[1]).longValue())
                .overdueCount(((Number) row[2]).longValue())
                .missedCount(((Number) row[3]).longValue())
                .orderViolationCount(((Number) row[4]).longValue())
                .affectedProtocols(((Number) row[5]).longValue())
                .affectedSteps(((Number) row[6]).longValue())
                .build()
        ).collect(Collectors.toList());
    }
}
