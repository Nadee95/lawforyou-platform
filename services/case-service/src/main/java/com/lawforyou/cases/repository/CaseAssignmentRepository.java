package com.lawforyou.cases.repository;

import com.lawforyou.cases.entity.CaseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, UUID> {

    Optional<CaseAssignment> findByCaseIdAndActiveTrue(UUID caseId);

    List<CaseAssignment> findAllByCaseId(UUID caseId);
}

