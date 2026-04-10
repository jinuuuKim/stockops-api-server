package com.stockops.repository;

import com.stockops.entity.Center;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CenterRepository extends JpaRepository<Center, Long> {
    Optional<Center> findByCode(String code);
    boolean existsByCode(String code);
}
