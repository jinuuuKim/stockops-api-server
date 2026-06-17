package com.stockops.settings;

public record SystemGeneralDto(
        long userCount,
        long centerCount,
        long warehouseCount,
        long productCount,
        long purchaseOrderCount,
        boolean bedrockEnabled,
        boolean vertexEnabled,
        String businessZone,
        String activeProfile
) {}
