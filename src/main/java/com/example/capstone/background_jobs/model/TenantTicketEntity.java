package com.example.capstone.background_jobs.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_ticket")
public class TenantTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id")
    private Integer tenantId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "finding_id")
    private String findingId;

    // Constructors
    public TenantTicketEntity() {
    }

    public TenantTicketEntity(Integer tenantId, String ticketId, String findingId) {
        this.tenantId = tenantId;
        this.ticketId = ticketId;
        this.findingId = findingId;
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getFindingId() {
        return findingId;
    }

    public void setFindingId(String findingId) {
        this.findingId = findingId;
    }
}


