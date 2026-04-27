package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ProtocolInstanceStats;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolInstanceStatsRepository extends ReadOnlyRepository<ProtocolInstanceStats, UUID> {

    List<ProtocolInstanceStats> findAllByProtocolInstanceIdIn(Collection<UUID> ids);

    @Query(value = """
            SELECT pis.* FROM protocol_instance_stats pis
            JOIN protocol_instance pi ON pi.id = pis.protocol_instance_id
            WHERE pi.protocol_definition_id = :protocolDefinitionId
            """, nativeQuery = true)
    List<ProtocolInstanceStats> findByProtocolDefinitionId(@Param("protocolDefinitionId") UUID protocolDefinitionId);
}
