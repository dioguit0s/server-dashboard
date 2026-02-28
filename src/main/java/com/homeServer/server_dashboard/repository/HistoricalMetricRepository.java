package com.homeServer.server_dashboard.repository;

import com.homeServer.server_dashboard.model.HistoricalMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface HistoricalMetricRepository extends JpaRepository<HistoricalMetric, Long> {
    List<HistoricalMetric> findByRecordedAtAfterOrderByRecordedAtAsc(LocalDateTime timeThreshold);
}
