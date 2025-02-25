package com.example.capstone.background_jobs.repository;

import com.example.capstone.background_jobs.model.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    TenantEntity findByName(String name);
}

