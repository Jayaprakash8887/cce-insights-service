package org.openphc.cce.insights.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.EventLog;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.ProtocolInstanceStats;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.EventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceStatsRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.service.PatientTimelineService;
import org.openphc.cce.insights.service.ProtocolDefinitionHelper;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/insights/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientTimelineService patientTimelineService;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final ProtocolInstanceStatsRepository protocolInstanceStatsRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final EventLogRepository eventLogRepository;
    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolDefinitionHelper protocolDefinitionHelper;
    private final ObjectMapper objectMapper;

    @GetMapping("/{patientId}/compliance-timeline")
    public ResponseEntity<ApiResponse<PatientTimelineDto>> getComplianceTimeline(
            @PathVariable String patientId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        PatientTimelineDto timeline = patientTimelineService.getTimeline(patientId);
        return ResponseEntity.ok(ApiResponse.ok(timeline));
    }

    @GetMapping("/{patientId}/protocol-tracking")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProtocolTracking(
            @PathVariable String patientId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);
        List<UUID> ids = instances.stream().map(ProtocolInstance::getId).toList();
        Map<UUID, ProtocolInstanceStats> statsMap = protocolInstanceStatsRepository.findAllByProtocolInstanceIdIn(ids)
                .stream().collect(Collectors.toMap(ProtocolInstanceStats::getProtocolInstanceId, s -> s));

        List<Map<String, Object>> result = instances.stream().map(pi -> {
            ProtocolInstanceStats s = statsMap.get(pi.getId());
            long total = s != null ? s.getTotalSteps() : 0;
            long completed = s != null ? s.getCompletedSteps() : 0;
            double rate = total > 0 ? Math.round((double) completed / total * 100.0) / 100.0 : 0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("protocolInstanceId", pi.getId());
            map.put("protocolCanonical", pi.getProtocolCanonical());
            map.put("enrolledAt", pi.getEnrolledAt());
            map.put("status", pi.getStatus());
            map.put("complianceRate", rate);
            map.put("stepsCompleted", completed);
            map.put("totalSteps", total);
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/protocol-tracking/{protocolInstanceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProtocolTrackingDetail(
            @PathVariable String patientId,
            @PathVariable UUID protocolInstanceId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);
        ProtocolInstance pi = instances.stream()
                .filter(p -> p.getId().equals(protocolInstanceId))
                .findFirst()
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Protocol instance not found: " + protocolInstanceId));

        List<StepInstance> steps = stepInstanceRepository
                .findByProtocolInstanceIdOrderByDueDateAsc(protocolInstanceId);
        List<Deviation> deviations = deviationRepository.findByProtocolInstanceId(protocolInstanceId);

        long completed = steps.stream().filter(s -> s.getCompletedAt() != null).count();
        double rate = steps.isEmpty() ? 0 : Math.round((double) completed / steps.size() * 100.0) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolInstanceId", pi.getId());
        result.put("patientId", pi.getPatientId());
        result.put("protocolCanonical", pi.getProtocolCanonical());
        result.put("status", pi.getStatus());
        result.put("enrolledAt", pi.getEnrolledAt());
        result.put("complianceRate", rate);

        List<Map<String, Object>> stepList = steps.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("stepInstanceId", s.getId());
            sm.put("actionId", s.getActionId());
            sm.put("state", s.getState());
            sm.put("dueDate", s.getDueDate());
            if (s.getCompletedAt() != null) sm.put("completedAt", s.getCompletedAt());
            if (s.getCompletionStatus() != null) sm.put("completionStatus", s.getCompletionStatus());
            if (s.getCompletedBySource() != null) sm.put("completedBySource", s.getCompletedBySource());
            if (s.getOverdueDate() != null) sm.put("overdueDate", s.getOverdueDate());
            if (s.getMissedDate() != null) sm.put("missedDate", s.getMissedDate());
            return sm;
        }).collect(Collectors.toList());
        result.put("steps", stepList);

        List<Map<String, Object>> devList = deviations.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("deviationId", d.getId());
            dm.put("stepInstanceId", d.getStepInstanceId());
            dm.put("deviationType", d.getDeviationType());
            dm.put("detectedAt", d.getDetectedAt());
            return dm;
        }).collect(Collectors.toList());
        result.put("deviations", devList);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPatientEvents(
            @PathVariable String patientId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit) {
        String subject = "Patient/" + patientId;
        List<EventLog> events = eventLogRepository.findPatientEventsFiltered(
                subject, resourceType, source, startDate, endDate, limit);

        List<Map<String, Object>> result = events.stream()
                .map(e -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("eventId", e.getId());
                    map.put("cloudeventsId", e.getCloudeventsId());
                    map.put("type", e.getType());
                    map.put("eventTime", e.getEventTime());
                    map.put("source", e.getSource());
                    map.put("resourceType", e.getResourceType());
                    map.put("processingStatus", e.getProcessingStatus());
                    map.put("facilityId", e.getFacilityId());
                    if (e.getProtocolInstanceId() != null)
                        map.put("protocolInstanceId", e.getProtocolInstanceId());
                    if (e.getActionId() != null)
                        map.put("actionId", e.getActionId());
                    if (e.getMatchedStepInstanceId() != null)
                        map.put("matchedStepInstanceId", e.getMatchedStepInstanceId());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/deviations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPatientDeviations(
            @PathVariable String patientId,
            @RequestParam(required = false) String deviationType,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);

        // Build actionId→title map from protocol definitions
        Map<String, String> stepTitles = new HashMap<>();
        for (ProtocolInstance pi : instances) {
            if (pi.getProtocolDefinitionId() != null) {
                stepTitles.putAll(protocolDefinitionHelper.resolveStepTitles(pi.getProtocolDefinitionId()));
            }
        }

        // Batch-load all steps for all instances (single query)
        List<UUID> instanceIds = instances.stream().map(ProtocolInstance::getId).toList();
        Map<UUID, String> stepActionIds = new HashMap<>();
        for (StepInstance si : stepInstanceRepository.findByProtocolInstanceIdIn(instanceIds)) {
            stepActionIds.put(si.getId(), si.getActionId());
        }

        List<Map<String, Object>> result = instances.stream()
                .flatMap(pi -> deviationRepository.findByProtocolInstanceId(pi.getId()).stream()
                        .map(d -> {
                            String actionId = stepActionIds.get(d.getStepInstanceId());
                            String stepName = actionId != null ? stepTitles.getOrDefault(actionId, ProtocolDefinitionHelper.formatActionId(actionId)) : null;
                            Map<String, Object> metadata = parseMetadata(d.getMetadata());
                            String description = buildDescription(d.getDeviationType().name(), stepName, metadata, stepTitles);
                            Map<String, Object> map = new LinkedHashMap<>();
                            map.put("deviationId", d.getId());
                            map.put("protocolInstanceId", pi.getId());
                            map.put("protocolCanonical", pi.getProtocolCanonical());
                            map.put("stepInstanceId", d.getStepInstanceId());
                            map.put("actionId", actionId);
                            map.put("stepName", stepName);
                            map.put("deviationType", d.getDeviationType());
                            map.put("detectedAt", d.getDetectedAt());
                            map.put("metadata", metadata);
                            map.put("description", description);
                            return map;
                        }))
                .filter(m -> deviationType == null ||
                        m.get("deviationType").toString().equalsIgnoreCase(deviationType))
                .filter(m -> startDate == null ||
                        !((OffsetDateTime) m.get("detectedAt")).isBefore(startDate))
                .filter(m -> endDate == null ||
                        !((OffsetDateTime) m.get("detectedAt")).isAfter(endDate))
                .sorted(Comparator.comparing(m -> ((OffsetDateTime) m.get("detectedAt")),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String buildDescription(String deviationType, String stepName,
                                     Map<String, Object> metadata,
                                     Map<String, String> stepTitles) {
        String step = stepName != null ? stepName : "Unknown Step";
        switch (deviationType) {
            case "ORDER_VIOLATION":
                String completedActionId = (String) metadata.get("completedActionId");
                List<String> prereqs = metadata.get("incompletePrerequisites") instanceof List
                        ? (List<String>) metadata.get("incompletePrerequisites")
                        : List.of();
                String completedName = completedActionId != null
                        ? stepTitles.getOrDefault(completedActionId, ProtocolDefinitionHelper.formatActionId(completedActionId))
                        : step;
                if (!prereqs.isEmpty()) {
                    String prereqNames = prereqs.stream()
                            .map(id -> stepTitles.getOrDefault(id, ProtocolDefinitionHelper.formatActionId(id)))
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    return completedName + " completed before " + prereqNames;
                }
                return completedName + " completed out of order";
            case "OVERDUE":
                return step + " is overdue";
            case "MISSED":
                return step + " was missed";
            default:
                return step;
        }
    }
}
