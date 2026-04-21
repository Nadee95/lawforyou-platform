package com.lawforyou.cases.repository;

import com.lawforyou.cases.entity.Case;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    Page<Case> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Case> findAllByTenantIdAndStatus(UUID tenantId, CaseStatus status, Pageable pageable);

    Page<Case> findAllByTenantIdAndCaseType(UUID tenantId, CaseType caseType, Pageable pageable);

    Page<Case> findAllByTenantIdAndStatusAndCaseType(
            UUID tenantId, CaseStatus status, CaseType caseType, Pageable pageable);
}

