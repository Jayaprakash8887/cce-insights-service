package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Shared helper for resolving protocol definition metadata (action titles, ordered actions).
 * Cached to avoid repeated JSON parsing of the same PlanDefinition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProtocolDefinitionHelper {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Resolve ordered action list from PlanDefinition JSON.
     * Returns list of [actionId, title] pairs in definition order.
     */
    @Cacheable(value = "lookups", key = "'ordered-actions-' + #protocolDefinitionId")
    public List<String[]> resolveOrderedActions(UUID protocolDefinitionId) {
        List<String[]> actions = new ArrayList<>();
        if (protocolDefinitionId == null) return actions;
        try {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId).orElse(null);
            if (pd != null && pd.getDefinition() != null) {
                JsonNode root = objectMapper.readTree(pd.getDefinition());
                JsonNode actionNodes = root.get("action");
                if (actionNodes != null && actionNodes.isArray()) {
                    for (JsonNode action : actionNodes) {
                        String id = action.has("id") ? action.get("id").asText() : null;
                        String title = action.has("title") ? action.get("title").asText() : null;
                        if (id != null) {
                            actions.add(new String[]{id, title != null ? title : formatActionId(id)});
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse protocol definition {}: {}", protocolDefinitionId, e.getMessage());
        }
        return actions;
    }

    /**
     * Resolve actionId → title map from PlanDefinition JSON.
     */
    @Cacheable(value = "lookups", key = "'step-titles-' + #protocolDefinitionId")
    public Map<String, String> resolveStepTitles(UUID protocolDefinitionId) {
        Map<String, String> titles = new LinkedHashMap<>();
        List<String[]> actions = resolveOrderedActions(protocolDefinitionId);
        for (String[] pair : actions) {
            titles.put(pair[0], pair[1]);
        }
        return titles;
    }

    /**
     * Format an action ID into a human-readable step name.
     * e.g., "lab-results" → "Lab Results"
     */
    public static String formatActionId(String actionId) {
        if (actionId == null) return "Unknown Step";
        return Arrays.stream(actionId.split("-"))
                .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(actionId);
    }
}
