package com.stockops.integration.sensimul;

import java.net.URI;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.stockops.exception.InvalidOperationException;

/**
 * HTTP client for Sensimul controller management form routes.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Service
public class SensimulControllerClient {

    private final RestTemplate sensimulRestTemplate;

    private final SensimulProperties properties;

    /**
     * Creates the client.
     *
     * @param sensimulRestTemplate configured Sensimul HTTP client
     * @param properties Sensimul configuration properties
     */
    public SensimulControllerClient(final RestTemplate sensimulRestTemplate, final SensimulProperties properties) {
        this.sensimulRestTemplate = sensimulRestTemplate;
        this.properties = properties;
    }

    /**
     * Updates a Sensimul controller through the edit form route.
     *
     * @param siteId Sensimul site identifier
     * @param controllerId Sensimul controller identifier
     * @param request update payload
     * @throws InvalidOperationException when Sensimul rejects the request as invalid
     * @throws SensimulIntegrationException when Sensimul is unavailable or returns a server-side error
     */
    public void updateController(final String siteId, final String controllerId, final ControllerUpdateRequest request) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("status", request.status());
        form.add("output_level", String.valueOf(request.outputLevel()));
        executeFormPost(buildUri("sites", siteId, "controllers", controllerId, "edit"), form,
                "update controller " + controllerId + " for site " + siteId);
    }

    /**
     * Creates a new Sensimul controller through the create form route.
     *
     * @param siteId Sensimul site identifier
     * @param request create payload
     * @throws InvalidOperationException when Sensimul rejects the request as invalid
     * @throws SensimulIntegrationException when Sensimul is unavailable or returns a server-side error
     */
    public void createController(final String siteId, final ControllerCreateRequest request) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", request.name());
        form.add("type", request.type());
        executeFormPost(buildUri("sites", siteId, "controllers"), form, "create controller for site " + siteId);
    }

    /**
     * Deletes a Sensimul controller through the delete form route.
     *
     * @param siteId Sensimul site identifier
     * @param controllerId Sensimul controller identifier
     * @throws InvalidOperationException when Sensimul rejects the request as invalid
     * @throws SensimulIntegrationException when Sensimul is unavailable or returns a server-side error
     */
    public void deleteController(final String siteId, final String controllerId) {
        executeFormPost(buildUri("sites", siteId, "controllers", controllerId, "delete"), new LinkedMultiValueMap<>(),
                "delete controller " + controllerId + " for site " + siteId);
    }

    private void executeFormPost(final URI uri, final MultiValueMap<String, String> form, final String operation) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            final ResponseEntity<String> response = sensimulRestTemplate.exchange(uri, HttpMethod.POST,
                    new HttpEntity<>(form, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()) {
                return;
            }
            if (response.getStatusCode().is4xxClientError()) {
                throw new InvalidOperationException(buildMessage(operation, response));
            }
            if (response.getStatusCode().is5xxServerError()) {
                throw new SensimulIntegrationException(buildMessage(operation, response));
            }
            throw new SensimulIntegrationException("Unexpected Sensimul response while attempting to " + operation
                    + ": HTTP " + response.getStatusCode().value());
        } catch (InvalidOperationException | SensimulIntegrationException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new SensimulIntegrationException("Failed to " + operation + " via Sensimul", ex);
        }
    }

    private String buildMessage(final String operation, final ResponseEntity<String> response) {
        final String body = response.getBody();
        final String suffix = body == null || body.isBlank() ? "" : ": " + body.trim();
        return "Sensimul failed to " + operation + " (HTTP " + response.getStatusCode().value() + ")" + suffix;
    }

    private URI buildUri(final String... pathSegments) {
        final StringBuilder builder = new StringBuilder(properties.getNormalizedBaseUrl());
        for (String pathSegment : pathSegments) {
            builder.append('/').append(pathSegment);
        }
        return URI.create(builder.toString());
    }
}
