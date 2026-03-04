package com.homeServer.server_dashboard.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class HistoricalMetric {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long identifier;
    private Instant recordedAt;
    private Double cpuUsagePercentage;
    private Double ramUsagePercentage;
    private Double cpuTemperature;

    public HistoricalMetric() {}

    public Long getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Long identifier) {
        this.identifier = identifier;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Double getCpuUsagePercentage() {
        return cpuUsagePercentage;
    }

    public void setCpuUsagePercentage(Double cpuUsagePercentage) {
        this.cpuUsagePercentage = cpuUsagePercentage;
    }

    public Double getRamUsagePercentage() {
        return ramUsagePercentage;
    }

    public void setRamUsagePercentage(Double ramUsagePercentage) {
        this.ramUsagePercentage = ramUsagePercentage;
    }

    public Double getCpuTemperature() {
        return cpuTemperature;
    }

    public void setCpuTemperature(Double cpuTemperature) {
        this.cpuTemperature = cpuTemperature;
    }
}
