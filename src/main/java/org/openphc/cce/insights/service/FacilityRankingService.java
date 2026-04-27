package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.FacilityStats;
import org.openphc.cce.insights.domain.repository.FacilityStatsRepository;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityRankingService {

    private final FacilityStatsRepository facilityStatsRepository;

    @Cacheable(value = "analytics", key = "'rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(OffsetDateTime startDate, OffsetDateTime endDate,
                                                 String sortBy, String order, int limit) {
        // TODO: startDate/endDate are accepted but not applied — facility_stats is a materialized view
        //       that aggregates all-time data. Date filtering would require a parameterized query/view.
        List<FacilityStats> allStats = facilityStatsRepository.findAll();

        List<FacilityRankingDto> rankings = allStats.stream().map(fs -> {
            double complianceRate = fs.getTotalSteps() > 0
                    ? Math.round((double) fs.getCompletedSteps() / fs.getTotalSteps() * 1000.0) / 10.0
                    : 100.0;

            return FacilityRankingDto.builder()
                    .facilityId(fs.getFacilityId())
                    .facilityName(fs.getFacilityName() != null ? fs.getFacilityName() : fs.getFacilityId())
                    .totalEvents(fs.getTotalEvents())
                    .totalEnrollments(fs.getTotalEnrollments())
                    .activeDeviations(fs.getTotalDeviations())
                    .complianceRate(complianceRate)
                    .build();
        }).collect(Collectors.toList());

        Comparator<FacilityRankingDto> comparator = switch (sortBy != null ? sortBy : "complianceRate") {
            case "complianceRate" -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
            case "deviationCount" -> Comparator.comparingLong(FacilityRankingDto::getActiveDeviations);
            case "eventVolume", "totalEvents" -> Comparator.comparingLong(FacilityRankingDto::getTotalEvents);
            default -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
        };

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        rankings.sort(comparator);

        int rank = 1;
        List<FacilityRankingDto> ranked = new ArrayList<>();
        for (FacilityRankingDto dto : rankings) {
            if (rank > limit) break;
            ranked.add(FacilityRankingDto.builder()
                    .rank(rank++)
                    .facilityId(dto.getFacilityId())
                    .facilityName(dto.getFacilityName())
                    .totalEvents(dto.getTotalEvents())
                    .totalEnrollments(dto.getTotalEnrollments())
                    .activeDeviations(dto.getActiveDeviations())
                    .complianceRate(dto.getComplianceRate())
                    .build());
        }
        return ranked;
    }
}
