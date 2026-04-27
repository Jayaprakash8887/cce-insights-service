package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.ProtocolInstanceStats;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceSummaryService {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final ProtocolInstanceStatsRepository protocolInstanceStatsRepository;
    private final EventLogRepository eventLogRepository;

    @Cacheable(value = "analytics", key = "'compliance-' + #protocolDefinitionId")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<ProtocolInstance> instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        if (instances.isEmpty()) {
            return buildEmptySummary(pd);
        }

        Map<String, Long> statusBreakdown = instances.stream()
                .collect(Collectors.groupingBy(pi -> pi.getStatus().name().toLowerCase(), Collectors.counting()));

        List<ProtocolInstanceStats> statsList = protocolInstanceStatsRepository.findByProtocolDefinitionId(protocolDefinitionId);

        long totalSteps = 0, completed = 0, onTime = 0, late = 0, early = 0, overdue = 0, missed = 0, pending = 0;
        long totalDeviations = 0, overdueDeviations = 0, missedDeviations = 0, orderViolationDeviations = 0;

        for (ProtocolInstanceStats s : statsList) {
            totalSteps += s.getTotalSteps();
            completed += s.getCompletedSteps();
            onTime += s.getOnTimeSteps();
            late += s.getLateSteps();
            early += s.getEarlySteps();
            overdue += s.getOverdueSteps();
            missed += s.getMissedSteps();
            pending += s.getPendingSteps();
            totalDeviations += s.getTotalDeviations();
            overdueDeviations += s.getOverdueDeviations();
            missedDeviations += s.getMissedDeviations();
            orderViolationDeviations += s.getOrderViolationDeviations();
        }

        // Count skipped as completed for rate calculation
        long skipped = statsList.stream().mapToInt(ProtocolInstanceStats::getSkippedSteps).sum();
        long effectiveCompleted = completed + skipped;
        double complianceRate = totalSteps > 0 ? (double) effectiveCompleted / totalSteps : 0.0;

        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(instances.size())
                .statusBreakdown(statusBreakdown)
                .complianceRate(Math.round(complianceRate * 100.0) / 100.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(totalSteps).completed(effectiveCompleted).onTime(onTime)
                        .late(late).early(early).overdue(overdue).missed(missed).pending(pending)
                        .build())
                .deviationCount(totalDeviations)
                .deviationBreakdown(Map.of("overdue", overdueDeviations, "missed", missedDeviations, "orderViolation", orderViolationDeviations))
                .build();
    }

    @Cacheable(value = "analytics", key = "'protocol-patients-' + #protocolDefinitionId + '-' + #statusFilter + '-' + #limit")
    public List<PatientComplianceDto> getProtocolPatients(UUID protocolDefinitionId, String statusFilter, int limit) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<ProtocolInstance> instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        List<UUID> instanceIds = instances.stream().map(ProtocolInstance::getId).toList();
        Map<UUID, ProtocolInstanceStats> statsMap = protocolInstanceStatsRepository.findAllByProtocolInstanceIdIn(instanceIds)
                .stream().collect(Collectors.toMap(ProtocolInstanceStats::getProtocolInstanceId, Function.identity()));

        List<PatientComplianceDto> results = new ArrayList<>();

        for (ProtocolInstance pi : instances) {
            ProtocolInstanceStats stats = statsMap.get(pi.getId());
            int total = stats != null ? stats.getTotalSteps() : 0;
            int comp = stats != null ? stats.getCompletedSteps() + stats.getSkippedSteps() : 0;
            double rate = total > 0 ? (double) comp / total : 0.0;
            String category = computeCategory(stats);

            if (statusFilter != null && !statusFilter.equalsIgnoreCase(category)) {
                continue;
            }

            long activeDevs = stats != null ? stats.getTotalDeviations() : 0;

            results.add(PatientComplianceDto.builder()
                    .patientId(pi.getPatientId())
                    .protocolInstanceId(pi.getId().toString())
                    .protocolCanonical(pi.getProtocolCanonical())
                    .enrolledAt(pi.getEnrolledAt())
                    .status(pi.getStatus().name().toLowerCase())
                    .complianceRate(Math.round(rate * 100.0) / 100.0)
                    .complianceCategory(category)
                    .stepsCompleted(comp)
                    .totalSteps(total)
                    .activeDeviations(activeDevs)
                    .build());

            if (results.size() >= limit) break;
        }
        return results;
    }

    @Cacheable(value = "analytics", key = "'facility-' + #facilityId")
    public FacilitySummaryDto getFacilityComplianceSummary(String facilityId) {
        List<Object[]> rows = eventLogRepository.findPatientsByFacility(facilityId);
        Set<UUID> facilityInstanceIds = new HashSet<>();
        Set<String> patients = new LinkedHashSet<>();
        for (Object[] row : rows) {
            patients.add((String) row[1]);
            facilityInstanceIds.add((UUID) row[2]);
        }

        List<ProtocolInstance> facilityInstances = facilityInstanceIds.isEmpty()
                ? Collections.emptyList()
                : protocolInstanceRepository.findAllById(facilityInstanceIds);

        Map<UUID, ProtocolInstanceStats> statsMap = facilityInstanceIds.isEmpty()
                ? Collections.emptyMap()
                : protocolInstanceStatsRepository.findAllByProtocolInstanceIdIn(facilityInstanceIds)
                        .stream().collect(Collectors.toMap(ProtocolInstanceStats::getProtocolInstanceId, Function.identity()));

        Map<UUID, List<ProtocolInstance>> byProtocol = facilityInstances.stream()
                .collect(Collectors.groupingBy(ProtocolInstance::getProtocolDefinitionId));

        List<FacilitySummaryDto.ProtocolBreakdown> breakdowns = new ArrayList<>();
        long totalCompleted = 0, totalSteps = 0;

        for (Map.Entry<UUID, List<ProtocolInstance>> entry : byProtocol.entrySet()) {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(entry.getKey()).orElse(null);
            if (pd == null) continue;

            List<ProtocolInstance> pInstances = entry.getValue();
            long pCompleted = 0, pTotal = 0, activeDevs = 0;
            for (ProtocolInstance pi : pInstances) {
                ProtocolInstanceStats stats = statsMap.get(pi.getId());
                if (stats != null) {
                    pTotal += stats.getTotalSteps();
                    pCompleted += stats.getCompletedSteps();
                    activeDevs += stats.getTotalDeviations();
                }
            }
            totalCompleted += pCompleted;
            totalSteps += pTotal;

            double pRate = pTotal > 0 ? Math.round((double) pCompleted / pTotal * 100.0) / 100.0 : 0;
            breakdowns.add(FacilitySummaryDto.ProtocolBreakdown.builder()
                    .protocolDefinitionId(entry.getKey().toString())
                    .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                    .enrollments(pInstances.size())
                    .complianceRate(pRate)
                    .activeDeviations(activeDevs)
                    .build());
        }

        double overallRate = totalSteps > 0 ? Math.round((double) totalCompleted / totalSteps * 100.0) / 100.0 : 0;

        return FacilitySummaryDto.builder()
                .facilityId(facilityId)
                .totalPatients(patients.size())
                .totalEnrollments(facilityInstances.size())
                .overallComplianceRate(overallRate)
                .protocolBreakdown(breakdowns)
                .build();
    }

    private String computeCategory(ProtocolInstanceStats stats) {
        if (stats == null) return "on_track";
        if (stats.getMissedSteps() > 0) return "non_compliant";
        if (stats.getOverdueSteps() > 0) return "at_risk";
        return "on_track";
    }

    private ComplianceSummaryDto buildEmptySummary(ProtocolDefinition pd) {
        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(pd.getId())
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(0)
                .statusBreakdown(Map.of())
                .complianceRate(0.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                .deviationCount(0)
                .deviationBreakdown(Map.of("overdue", 0L, "missed", 0L, "orderViolation", 0L))
                .build();
    }
}
