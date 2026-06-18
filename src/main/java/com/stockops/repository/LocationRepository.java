package com.stockops.repository;

import com.stockops.entity.Location;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);

    List<Location> findByType(String type);

    List<Location> findByWarehouseId(Long warehouseId);

    List<Location> findByWarehouseIdIn(Collection<Long> warehouseIds);

    /**
     * Loads locations by id with their warehouse and center eagerly fetched in a single query.
     * Used by scope resolution so warehouse/center ids are read from the fetched graph instead of
     * triggering a lazy SELECT per location.
     *
     * @param ids location identifiers
     * @return matching locations with warehouse and center initialized
     */
    @Query("SELECT l FROM Location l LEFT JOIN FETCH l.warehouse w LEFT JOIN FETCH w.center WHERE l.id IN :ids")
    List<Location> findByIdInWithWarehouseAndCenter(@Param("ids") Collection<Long> ids);
}
