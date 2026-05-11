package com.stockops.environment.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Sensimul MQTT telemetry subscriber.
 *
 * @author StockOps Team
 * @since 1.0
 */
@ConfigurationProperties(prefix = "stockops.mqtt-ingestion")
public class MqttIngestionProperties {

    private String brokerUrl = "tcp://localhost:1883";
    private int qos = 1;
    private String clientId;
    private boolean enabled = true;

    /**
     * Returns the MQTT broker URL.
     *
     * @return broker URL
     */
    public String getBrokerUrl() {
        return brokerUrl;
    }

    /**
     * Sets the MQTT broker URL.
     *
     * @param brokerUrl broker URL
     */
    public void setBrokerUrl(final String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    /**
     * Returns the subscription QoS.
     *
     * @return qos value
     */
    public int getQos() {
        return qos;
    }

    /**
     * Sets the subscription QoS.
     *
     * @param qos qos value
     */
    public void setQos(final int qos) {
        this.qos = qos;
    }

    /**
     * Returns the MQTT client id.
     *
     * @return configured client id
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the MQTT client id.
     *
     * @param clientId client id
     */
    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns whether ingestion is enabled.
     *
     * @return true when subscriber should start
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether ingestion is enabled.
     *
     * @param enabled enable flag
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
