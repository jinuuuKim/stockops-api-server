package com.stockops.settings;

import com.stockops.ai.bedrock.BedrockAiProperties;
import com.stockops.ai.gcp.VertexAiProperties;
import com.stockops.repository.CenterRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.UserRepository;
import com.stockops.repository.WarehouseRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final UserRepository userRepository;
    private final CenterRepository centerRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BedrockAiProperties bedrockProperties;
    private final VertexAiProperties vertexProperties;
    private final Environment environment;

    public SettingsService(
            final UserRepository userRepository,
            final CenterRepository centerRepository,
            final WarehouseRepository warehouseRepository,
            final ProductRepository productRepository,
            final PurchaseOrderRepository purchaseOrderRepository,
            final BedrockAiProperties bedrockProperties,
            final VertexAiProperties vertexProperties,
            final Environment environment) {
        this.userRepository = userRepository;
        this.centerRepository = centerRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.bedrockProperties = bedrockProperties;
        this.vertexProperties = vertexProperties;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public SystemGeneralDto getGeneralSettings() {
        final String[] profiles = environment.getActiveProfiles();
        final String activeProfile = profiles.length > 0 ? profiles[0] : "default";
        final String businessZone = environment.getProperty("stockops.ai.business-zone", "Asia/Seoul");

        return new SystemGeneralDto(
                userRepository.count(),
                centerRepository.count(),
                warehouseRepository.count(),
                productRepository.count(),
                purchaseOrderRepository.count(),
                bedrockProperties.isEnabled(),
                vertexProperties.isEnabled(),
                businessZone,
                activeProfile);
    }

    public SystemIntegrationsDto getIntegrations() {
        final var bedrock = new SystemIntegrationsDto.BedrockIntegration(
                bedrockProperties.isEnabled(),
                bedrockProperties.getRegion(),
                bedrockProperties.generationModelReference(),
                hasText(bedrockProperties.getKnowledgeBaseId()),
                hasText(bedrockProperties.getAgentId()));

        final var vertex = new SystemIntegrationsDto.VertexIntegration(
                vertexProperties.isEnabled(),
                vertexProperties.getLocation(),
                vertexProperties.getModelId(),
                hasText(vertexProperties.getCredentialsJson()));

        return new SystemIntegrationsDto(bedrock, vertex);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
