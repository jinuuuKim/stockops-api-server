package com.stockops.controller;

import com.stockops.repository.ProductRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.UserRepository;
import com.stockops.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long totalOrders = purchaseOrderRepository.count();
        long lowStockCount = inventoryRepository.countLowStockItems(10);

        return ResponseEntity.ok(Map.of(
            "totalUsers", totalUsers,
            "totalProducts", totalProducts,
            "totalOrders", totalOrders,
            "lowStockCount", lowStockCount
        ));
    }

    @GetMapping("/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getMenus() {
        return ResponseEntity.ok(Map.of(
            "menus", java.util.List.of(
                Map.of("path", "/admin", "label", "대시보드"),
                Map.of("path", "/admin/notices", "label", "공지 관리"),
                Map.of("path", "/admin/audit-logs", "label", "감사 로그"),
                Map.of("path", "/admin/menus", "label", "메뉴 관리")
            )
        ));
    }
}