package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.ProtocolInstanceStats;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {

    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final ProtocolInstanceStatsRepository protocolInstanceStatsRepository;

    public void writeComplianceCsv(UUID protocolDefinitionId, String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate,
                                     OutputStream outputStream) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.println("patient_id,protocol_canonical,status,enrolled_at," +
                "total_steps,completed_steps,overdue_steps,missed_steps,compliance_rate");

        List<ProtocolInstance> instances;
        if (protocolDefinitionId != null) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else {
            instances = protocolInstanceRepository.findAll();
        }

        List<UUID> ids = instances.stream().map(ProtocolInstance::getId).toList();
        Map<UUID, ProtocolInstanceStats> statsMap = protocolInstanceStatsRepository.findAllByProtocolInstanceIdIn(ids)
                .stream().collect(Collectors.toMap(ProtocolInstanceStats::getProtocolInstanceId, Function.identity()));

        for (ProtocolInstance pi : instances) {
            ProtocolInstanceStats s = statsMap.get(pi.getId());
            long total = s != null ? s.getTotalSteps() : 0;
            long completed = s != null ? s.getCompletedSteps() : 0;
            long overdue = s != null ? s.getOverdueSteps() : 0;
            long missed = s != null ? s.getMissedSteps() : 0;
            double rate = total > 0 ? Math.round((double) completed / total * 100.0) / 100.0 : 0;

            writer.printf("%s,%s,%s,%s,%d,%d,%d,%d,%.2f%n",
                    escapeCsv(pi.getPatientId()),
                    escapeCsv(pi.getProtocolCanonical()),
                    pi.getStatus(),
                    pi.getEnrolledAt(),
                    total, completed, overdue, missed, rate);
        }
        writer.flush();
    }

    public List<Map<String, Object>> exportComplianceJson(UUID protocolDefinitionId, String facilityId,
                                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        List<ProtocolInstance> instances;
        if (protocolDefinitionId != null) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else {
            instances = protocolInstanceRepository.findAll();
        }

        List<UUID> ids = instances.stream().map(ProtocolInstance::getId).toList();
        Map<UUID, ProtocolInstanceStats> statsMap = protocolInstanceStatsRepository.findAllByProtocolInstanceIdIn(ids)
                .stream().collect(Collectors.toMap(ProtocolInstanceStats::getProtocolInstanceId, Function.identity()));

        List<Map<String, Object>> results = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            ProtocolInstanceStats s = statsMap.get(pi.getId());
            long total = s != null ? s.getTotalSteps() : 0;
            long completed = s != null ? s.getCompletedSteps() : 0;
            long overdue = s != null ? s.getOverdueSteps() : 0;
            long missed = s != null ? s.getMissedSteps() : 0;
            double rate = total > 0 ? Math.round((double) completed / total * 100.0) / 100.0 : 0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patientId", pi.getPatientId());
            row.put("protocolCanonical", pi.getProtocolCanonical());
            row.put("status", pi.getStatus());
            row.put("enrolledAt", pi.getEnrolledAt());
            row.put("totalSteps", total);
            row.put("completedSteps", completed);
            row.put("overdueSteps", overdue);
            row.put("missedSteps", missed);
            row.put("complianceRate", rate);
            results.add(row);
        }
        return results;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
