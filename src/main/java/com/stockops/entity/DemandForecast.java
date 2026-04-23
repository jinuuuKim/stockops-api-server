package com.stockops.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "demand_forecasts")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SQLRestriction("deleted = false")
public class DemandForecast extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "predicted_quantity", nullable = false)
    private BigDecimal predictedQuantity;

    @Column(name = "confidence_lower")
    private BigDecimal confidenceLower;

    @Column(name = "confidence_upper")
    private BigDecimal confidenceUpper;

    @Column(name = "model_version")
    private String modelVersion = "v1.0";
}