package com.stockops.notification.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that auto-discovers all {@link WebhookProvider} Spring beans
 * and indexes them by {@link WebhookProvider#getProviderType()}.
 *
 * <p>Uses constructor injection of a list of all WebhookProvider beans,
 * then builds a lookup map keyed by provider type string.</p>
 *
 * @author StockOps Team
 * @since 1.0
 */
@Slf4j
@Component
public class WebhookProviderRegistry {

    private final Map<String, WebhookProvider> providers = new HashMap<>();

    /**
     * Constructs the registry by collecting all WebhookProvider beans.
     *
     * @param providerList all WebhookProvider implementations registered as Spring beans
     */
    public WebhookProviderRegistry(final List<WebhookProvider> providerList) {
        for (WebhookProvider provider : providerList) {
            String type = provider.getProviderType();
            if (providers.containsKey(type)) {
                log.warn("Duplicate WebhookProvider type '{}': overwriting {} with {}",
                        type, providers.get(type).getClass().getSimpleName(),
                        provider.getClass().getSimpleName());
            }
            providers.put(type, provider);
            log.info("Registered WebhookProvider: type={}, class={}",
                    type, provider.getClass().getSimpleName());
        }
    }

    /**
     * Looks up a provider by its type identifier.
     *
     * @param providerType the type string (e.g. "SLACK", "DISCORD")
     * @return matching provider, or empty if not registered
     */
    public Optional<WebhookProvider> getProvider(final String providerType) {
        return Optional.ofNullable(providers.get(providerType));
    }

    /**
     * Returns all registered provider types.
     *
     * @return set of registered provider type strings
     */
    public java.util.Set<String> getRegisteredTypes() {
        return providers.keySet();
    }
}