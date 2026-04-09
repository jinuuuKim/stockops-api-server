package com.stockops.repository;

import com.stockops.entity.Inbound;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundRepository extends JpaRepository<Inbound, Long> {

    List<Inbound> findByStatus(String status);

    long countByInboundDate(LocalDate inboundDate);
}
