package com.stockops.controller;

import com.stockops.dto.CreateInventoryTransferRequest;
import com.stockops.dto.InventoryTransferDTO;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.UserRepository;
import com.stockops.service.InventoryTransferService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for inventory transfer management.
 *
 * @author StockOps Team
 * @since 2.0
 * @see InventoryTransferService
 */
@RestController
@RequestMapping("/api/v1/inventory-transfers")
@RequiredArgsConstructor
public class InventoryTransferController {

    private final InventoryTransferService transferService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('INVENTORY_READ')")
    public ResponseEntity<List<InventoryTransferDTO>> getAllTransfers() {
        return ResponseEntity.ok(transferService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('INVENTORY_READ')")
    public ResponseEntity<InventoryTransferDTO> getTransferById(@PathVariable final Long id) {
        return ResponseEntity.ok(transferService.findById(id));
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission('INVENTORY_ADJUST_CREATE')")
    public ResponseEntity<InventoryTransferDTO> createTransfer(
            @Valid @RequestBody final CreateInventoryTransferRequest request,
            final Principal principal) {
        final Long userId = getCurrentUserId(principal);
        final InventoryTransferDTO created = transferService.createTransfer(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/inventory-transfers/" + created.id())).body(created);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@permissionChecker.hasPermission('INVENTORY_ADJUST_APPROVE')")
    public ResponseEntity<InventoryTransferDTO> completeTransfer(
            @PathVariable final Long id,
            final Principal principal) {
        final Long userId = getCurrentUserId(principal);
        return ResponseEntity.ok(transferService.completeTransfer(id, userId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('INVENTORY_ADJUST_APPROVE')")
    public ResponseEntity<InventoryTransferDTO> cancelTransfer(
            @PathVariable final Long id,
            final Principal principal) {
        final Long userId = getCurrentUserId(principal);
        return ResponseEntity.ok(transferService.cancelTransfer(id, userId));
    }

    private Long getCurrentUserId(final Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new InvalidOperationException("Authenticated user is required");
        }

        return userRepository.findByEmail(principal.getName())
                .map(user -> user.getId())
                .orElseThrow(() -> new InvalidOperationException("Authenticated user not found"));
    }
}
