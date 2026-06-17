package com.stockops.notification.webhook;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static catalog mapping alert types to their Korean alert name and the responsible role,
 * plus the notification role hierarchy used to escalate a message to superior roles.
 *
 * <p>Kept as code-level configuration for now; a future settings page can move this to a
 * seeded table without changing the rendering/routing contract.
 *
 * @author StockOps Team
 * @since 2.3
 */
public final class AlertTypeNotificationCatalog {

    /** Mapping of an alert type to its display name and base responsible role. */
    public record Mapping(String alertName, String baseRole) {
    }

    /**
     * Notification role hierarchy, lowest authority first. A target role's "superiors" are the
     * roles that appear after it in this list. There is no hierarchy column on the {@code roles}
     * table, so this ordering is the single source of truth for escalation.
     */
    public static final List<String> ROLE_RANK = List.of(
            "STORE_STAFF",
            "STORE_MANAGER",
            "WAREHOUSE_MANAGER",
            "CENTER_MANAGER",
            "GENERAL_ADMIN",
            "ADMIN");

    private static final Map<String, String> ROLE_LABEL_KO = Map.of(
            "ADMIN", "최고관리자",
            "GENERAL_ADMIN", "일반관리자",
            "CENTER_MANAGER", "센터관리자",
            "WAREHOUSE_MANAGER", "창고관리자",
            "STORE_MANAGER", "지점매니저",
            "STORE_STAFF", "지점직원");

    private static final Mapping DEFAULT_MAPPING = new Mapping("환경 알림", "WAREHOUSE_MANAGER");

    private AlertTypeNotificationCatalog() {
    }

    /**
     * Resolves the alert name and base role for an alert type. Matching is keyword-based and
     * case-insensitive so it tolerates both {@code TEMPERATURE_THRESHOLD} and {@code temperature}.
     *
     * @param alertType raw alert type (may be null)
     * @return the mapping, never null (falls back to a generic environment-alert mapping)
     */
    public static Mapping forAlertType(final String alertType) {
        if (alertType == null) {
            return DEFAULT_MAPPING;
        }
        final String t = alertType.toUpperCase(Locale.ROOT);
        if (t.contains("DOOR")) {
            return new Mapping("출입문 경보", "WAREHOUSE_MANAGER");
        }
        if (t.contains("TEMP") || t.contains("HUMIDITY") || t.contains("AIR") || t.contains("THRESHOLD")) {
            return new Mapping("센서 임계치 알림", "WAREHOUSE_MANAGER");
        }
        if (t.contains("STOCK") || t.contains("INVENTORY") || t.contains("EXPIRY")) {
            return new Mapping("재고 알림", "WAREHOUSE_MANAGER");
        }
        return DEFAULT_MAPPING;
    }

    /**
     * Returns the base role plus every superior role (the base role and all higher-authority roles),
     * de-duplicated and ordered from base upward. Used to escalate a notification up the chain.
     *
     * @param baseRole the responsible role
     * @return base role + superiors; a singleton of the base role if it is unknown
     */
    public static List<String> rolesIncludingSuperiors(final String baseRole) {
        final int idx = ROLE_RANK.indexOf(baseRole);
        if (idx < 0) {
            return List.of(baseRole);
        }
        return List.copyOf(ROLE_RANK.subList(idx, ROLE_RANK.size()));
    }

    /**
     * Korean label for a role name, or the raw role name when no label is registered.
     *
     * @param role role name (e.g. "WAREHOUSE_MANAGER")
     * @return Korean label (e.g. "창고관리자")
     */
    public static String roleLabelKo(final String role) {
        return ROLE_LABEL_KO.getOrDefault(role, role);
    }
}
