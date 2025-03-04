package com.example.capstone.background_jobs.repository;

import com.example.capstone.background_jobs.model.RunbookConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunbookConfigRepository extends JpaRepository<RunbookConfigEntity, Long> {

    // Find config by runbookId (the same string foreign key)
    RunbookConfigEntity findByRunbookId(String runbookId);
}

