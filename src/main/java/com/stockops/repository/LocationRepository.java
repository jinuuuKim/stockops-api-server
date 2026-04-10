package com.stockops.repository;

import com.stockops.entity.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);

    List<Location> findByType(String type);

    List<Location> findByWarehouseId(Long warehouseId);
}
