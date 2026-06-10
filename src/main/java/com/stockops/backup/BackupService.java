package com.stockops.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.entity.Center;
import com.stockops.entity.Role;
import com.stockops.entity.Warehouse;
import com.stockops.repository.CenterRepository;
import com.stockops.repository.RoleRepository;
import com.stockops.repository.WarehouseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackupService {

    private final CenterRepository centerRepository;
    private final WarehouseRepository warehouseRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public BackupService(
            final CenterRepository centerRepository,
            final WarehouseRepository warehouseRepository,
            final RoleRepository roleRepository,
            final ObjectMapper objectMapper) {
        this.centerRepository = centerRepository;
        this.warehouseRepository = warehouseRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportJson() {
        final List<Center> centers = centerRepository.findAll();
        final List<Warehouse> warehouses = warehouseRepository.findAllWithCenter();
        final List<Role> roles = roleRepository.findAll();

        final var export = new BackupExport(
                java.time.Instant.now(),
                "1.0",
                centers.stream()
                        .map(c -> new BackupExport.CenterExport(
                                c.getId(), c.getCode(), c.getName(), c.getAddress(), c.getPhone(), c.getStatus()))
                        .toList(),
                warehouses.stream()
                        .map(w -> new BackupExport.WarehouseExport(
                                w.getId(),
                                w.getCenter() != null ? w.getCenter().getId() : null,
                                w.getCode(), w.getName(), w.getAddress(), w.getPhone(),
                                w.getStatus() != null ? w.getStatus().name() : null))
                        .toList(),
                roles.stream()
                        .map(r -> new BackupExport.RoleExport(r.getId(), r.getName(), r.getDescription(), r.getCreatedAt()))
                        .toList());

        try {
            return objectMapper.writeValueAsBytes(export);
        } catch (final Exception e) {
            throw new RuntimeException("백업 데이터 직렬화 중 오류가 발생했습니다.", e);
        }
    }
}
