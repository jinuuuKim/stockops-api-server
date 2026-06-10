package com.stockops.backup;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(final BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export() {
        final byte[] json = backupService.exportJson();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stockops-backup.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
}
