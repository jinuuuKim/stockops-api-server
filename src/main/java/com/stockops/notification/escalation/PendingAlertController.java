package com.stockops.notification.escalation;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for pending environment alerts.
 * Supports creation, listing, and acknowledgment of alerts.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/pending-alerts")
@RequiredArgsConstructor
public class PendingAlertController {

    private final PendingAlertService pendingAlertService;

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('CENTER_READ')")
    public ResponseEntity<List<PendingAlertDTO>> listAlerts(
            @RequestParam(required = false) final PendingAlertStatus status) {
        final List<PendingAlert> alerts = status != null
                ? pendingAlertService.findByStatus(status)
                : pendingAlertService.findByStatus(PendingAlertStatus.PENDING);
        return ResponseEntity.ok(alerts.stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('CENTER_READ')")
    public ResponseEntity<PendingAlertDTO> getAlert(@PathVariable final Long id) {
        return ResponseEntity.ok(toDto(pendingAlertService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission('CENTER_CREATE')")
    public ResponseEntity<PendingAlertDTO> createAlert(@RequestBody final PendingAlertCreateRequest request) {
        final PendingAlert alert = pendingAlertService.createAlert(
                request.alertType(), request.centerId(), request.warehouseId(),
                request.sensorId(), request.message(), request.severity());
        return ResponseEntity.ok(toDto(alert));
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("@permissionChecker.hasPermission('CENTER_UPDATE')")
    public ResponseEntity<PendingAlertDTO> acknowledgeAlert(
            @PathVariable final Long alertId, final Principal principal) {
        final String username = principal != null ? principal.getName() : "SYSTEM";
        final PendingAlert acknowledged = pendingAlertService.acknowledge(alertId, username);
        return ResponseEntity.ok(toDto(acknowledged));
    }

    private PendingAlertDTO toDto(final PendingAlert alert) {
        return new PendingAlertDTO(
                alert.getId(),
                alert.getAlertType(),
                alert.getCenterId(),
                alert.getWarehouseId(),
                alert.getSensorId(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getCurrentLevel(),
                alert.getAcknowledgedAt(),
                alert.getAcknowledgedBy(),
                alert.getCreatedAt(),
                alert.getUpdatedAt());
    }
}