package com.example.capstone.background_jobs.repository;

import com.example.capstone.background_jobs.model.TenantTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantTicketRepository extends JpaRepository<TenantTicketEntity, Integer> {

    Optional<TenantTicketEntity> findByFindingId(String findingId);
}
