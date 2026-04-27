-- Demand forecasts table for ML predictions
CREATE TABLE demand_forecasts (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    forecast_date DATE NOT NULL,
    predicted_quantity DECIMAL(15,3) NOT NULL,
    confidence_lower DECIMAL(15,3),
    confidence_upper DECIMAL(15,3),
    model_version VARCHAR(50) DEFAULT 'v1.0',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_demand_forecasts_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_demand_forecasts_product_date ON demand_forecasts(product_id, forecast_date);
CREATE INDEX idx_demand_forecasts_date ON demand_forecasts(forecast_date);