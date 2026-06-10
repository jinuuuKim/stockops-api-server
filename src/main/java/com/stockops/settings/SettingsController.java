package com.stockops.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(final SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/general")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemGeneralDto> getGeneralSettings() {
        return ResponseEntity.ok(settingsService.getGeneralSettings());
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemIntegrationsDto> getIntegrations() {
        return ResponseEntity.ok(settingsService.getIntegrations());
    }
}
