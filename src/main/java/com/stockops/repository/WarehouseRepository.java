package com.stockops.repository;

import com.stockops.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @Query("SELECT w FROM Warehouse w LEFT JOIN FETCH w.center")
    List<Warehouse> findAllWithCenter();

    @Query("SELECT w FROM Warehouse w LEFT JOIN FETCH w.center WHERE w.id = :id")
    Optional<Warehouse> findByIdWithCenter(@Param("id") Long id);

    List<Warehouse> findByCenterId(Long centerId);
    Optional<Warehouse> findByCenterIdAndCode(Long centerId, String code);
    boolean existsByCenterIdAndCode(Long centerId, String code);

    @Query("SELECT w FROM Warehouse w WHERE w.center.id = :centerId AND w.status = com.stockops.entity.WarehouseStatus.ACTIVE")
    List<Warehouse> findActiveByCenterId(@Param("centerId") Long centerId);
}
