package com.stockops.controller;

import com.stockops.dto.CompleteCycleCountRequest;
import com.stockops.dto.CreateCycleCountRequest;
import com.stockops.dto.CycleCountDTO;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.UserRepository;
import com.stockops.service.CycleCountService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
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
 * Cycle count workflow API controller.
 * Allows managers to create, start, inspect, and complete location-based inventory counts.
 *
 * @author StockOps Team
 * @since 1.0
 * @see CycleCountService
 */
@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;
    private final UserRepository userRepository;

    /**
     * Creates a new cycle count.
     *
     * @param request cycle count creation payload
     * @param principal authenticated principal
     * @return created cycle count response
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CycleCountDTO> createCycleCount(@Valid @RequestBody final CreateCycleCountRequest request,
                                                          final Principal principal) {
        final CycleCountDTO created = cycleCountService.createCycleCount(request, getCurrentUserId(principal));
        return ResponseEntity.created(URI.create("/api/v1/cycle-counts/" + created.id())).body(created);
    }

    /**
     * Retrieves a cycle count by identifier.
     *
     * @param id cycle count identifier
     * @return cycle count response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CycleCountDTO> getCycleCount(@PathVariable final Long id) {
        return ResponseEntity.ok(cycleCountService.getCycleCount(id));
    }

    /**
     * Starts a pending cycle count.
     *
     * @param id cycle count identifier
     * @param principal authenticated principal
     * @return started cycle count response
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CycleCountDTO> startCycleCount(@PathVariable final Long id,
                                                         final Principal principal) {
        return ResponseEntity.ok(cycleCountService.startCycleCount(id, getCurrentUserId(principal)));
    }

    /**
     * Completes an in-progress cycle count with the final counted quantities.
     *
     * @param id cycle count identifier
     * @param request cycle count completion payload
     * @param principal authenticated principal
     * @return completed cycle count response
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CycleCountDTO> completeCycleCount(@PathVariable final Long id,
                                                            @Valid @RequestBody final CompleteCycleCountRequest request,
                                                            final Principal principal) {
        return ResponseEntity.ok(cycleCountService.completeCycleCount(id, request, getCurrentUserId(principal)));
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
