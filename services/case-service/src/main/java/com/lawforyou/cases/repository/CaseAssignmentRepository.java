package com.lawforyou.cases.repository;

import com.lawforyou.cases.entity.CaseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, UUID> {

    Optional<CaseAssignment> findByCaseIdAndActiveTrue(UUID caseId);

    List<CaseAssignment> findAllByCaseId(UUID caseId);

    /**
     * Batch-load active lawyer assignments for multiple cases in a single query.
     * Eliminates the N+1 problem in {@code CaseServiceImpl.findAll()}.
     *
     * @param caseIds collection of case UUIDs from the current page
     * @return list of active assignments for those cases
     */
    @Query("SELECT a FROM CaseAssignment a WHERE a.caseId IN :caseIds AND a.active = true")
    List<CaseAssignment> findActiveByCaseIdIn(@Param("caseIds") Collection<UUID> caseIds);

    /**
     * Convenience: returns a Map of caseId → lawyerId for the given case IDs.
     * Uses {@link #findActiveByCaseIdIn} — single DB round-trip regardless of page size.
     */
    default Map<UUID, UUID> activeLawyersByCaseId(Collection<UUID> caseIds) {
        return findActiveByCaseIdIn(caseIds).stream()
                .collect(Collectors.toMap(CaseAssignment::getCaseId, CaseAssignment::getLawyerId));
    }
}

