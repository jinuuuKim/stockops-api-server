package com.stockops.backup;

import java.time.Instant;
import java.util.List;

public record BackupExport(
        Instant exportedAt,
        String version,
        List<CenterExport> centers,
        List<WarehouseExport> warehouses,
        List<RoleExport> roles
) {
    public record CenterExport(Long id, String code, String name, String address, String phone, String status) {}

    public record WarehouseExport(Long id, Long centerId, String code, String name, String address, String phone, String status) {}

    public record RoleExport(Long id, String name, String description, Instant createdAt) {}
}
