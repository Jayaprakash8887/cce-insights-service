package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.FacilityStats;

import java.util.List;

public interface FacilityStatsRepository extends ReadOnlyRepository<FacilityStats, String> {

    List<FacilityStats> findAll();
}
