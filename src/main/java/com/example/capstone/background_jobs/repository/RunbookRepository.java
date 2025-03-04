package com.example.capstone.background_jobs.repository;

import com.example.capstone.background_jobs.model.RunbookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunbookRepository extends JpaRepository<RunbookEntity, Long> {

    // If you want to find by runbookId (the UUID string):
    RunbookEntity findByRunbookId(String runbookId);

    // If you want to find all runbooks belonging to a tenant:
    java.util.List<RunbookEntity> findByTenantId(Integer tenantId);
}
