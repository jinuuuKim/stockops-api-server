package com.stockops.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Cycle count completion request payload.
 *
 * @param items final counts for each cycle count item
 * @author StockOps Team
 * @since 1.0
 */
public record CompleteCycleCountRequest(
        @NotEmpty List<@Valid CompleteCycleCountItemRequest> items
) {
}
