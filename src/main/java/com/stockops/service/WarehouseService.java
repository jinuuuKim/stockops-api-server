package com.stockops.service;

import com.stockops.entity.Center;
import com.stockops.entity.Warehouse;
import com.stockops.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for Warehouse management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final CenterService centerService;

    public List<Warehouse> findAll() {
        return warehouseRepository.findAll();
    }

    public List<Warehouse> findByCenterId(Long centerId) {
        return warehouseRepository.findByCenterId(centerId);
    }

    public List<Warehouse> findActiveByCenterId(Long centerId) {
        return warehouseRepository.findActiveByCenterId(centerId);
    }

    public Warehouse findById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + id));
    }

    public Warehouse create(Long centerId, Warehouse warehouse) {
        Center center = centerService.findById(centerId);

        if (warehouseRepository.existsByCenterIdAndCode(centerId, warehouse.getCode())) {
            throw new RuntimeException("Warehouse code already exists in this center: " + warehouse.getCode());
        }

        warehouse.setCenter(center);
        warehouse.setStatus("ACTIVE");
        return warehouseRepository.save(warehouse);
    }

    public Warehouse update(Long id, Warehouse warehouse) {
        Warehouse existing = findById(id);
        existing.setName(warehouse.getName());
        existing.setAddress(warehouse.getAddress());
        existing.setPhone(warehouse.getPhone());
        existing.setStatus(warehouse.getStatus());
        return warehouseRepository.save(existing);
    }

    public void delete(Long id) {
        Warehouse warehouse = findById(id);
        warehouse.setStatus("CLOSED");
        warehouseRepository.save(warehouse);
    }
}
