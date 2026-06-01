package com.stockops.security;

/**
 * Supported authorization scope levels for data visibility.
 *
 * @author StockOps Team
 * @since 2.0
 */
public enum ScopeType {
    ADMIN,
    CENTER,
    WAREHOUSE,
    STORE;

    private static final String LEGACY_GLOBAL = "GLOBAL";

    public static ScopeType fromPersistedValue(final String value) {
        if (value == null) {
            return null;
        }

        final String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (LEGACY_GLOBAL.equals(normalized)) {
            return ADMIN;
        }

        try {
            return ScopeType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported scope type: " + value, exception);
        }
    }

    public String toPersistedValue() {
        return this == ADMIN ? LEGACY_GLOBAL : name();
    }
}
