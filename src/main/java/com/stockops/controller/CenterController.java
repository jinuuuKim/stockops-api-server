package com.stockops.controller;

import com.stockops.entity.Center;
import com.stockops.service.CenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Center management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/centers")
@RequiredArgsConstructor
public class CenterController {

    private final CenterService centerService;

    @GetMapping
    public List<Center> getAllCenters() {
        return centerService.findAll();
    }

    @GetMapping("/{id}")
    public Center getCenterById(@PathVariable Long id) {
        return centerService.findById(id);
    }

    @GetMapping("/code/{code}")
    public Center getCenterByCode(@PathVariable String code) {
        return centerService.findByCode(code);
    }

    @PostMapping
    public Center createCenter(@RequestBody Center center) {
        return centerService.create(center);
    }

    @PutMapping("/{id}")
    public Center updateCenter(@PathVariable Long id, @RequestBody Center center) {
        return centerService.update(id, center);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCenter(@PathVariable Long id) {
        centerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
