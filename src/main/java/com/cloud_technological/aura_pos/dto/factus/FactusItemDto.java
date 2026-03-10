package com.cloud_technological.aura_pos.dto.factus;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class FactusItemDto {
    @JsonProperty("code_reference")
    private String codeReference;

    @JsonProperty("name")
    private String name;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("discount_rate")
    private BigDecimal discountRate = BigDecimal.ZERO;

    // Precio unitario SIN IVA
    @JsonProperty("price")
    private BigDecimal price;

    // "19.00", "5.00", "0.00"
    @JsonProperty("tax_rate")
    private String taxRate;

    // 1=IVA, 3=No aplica (exento/excluido)
    @JsonProperty("tribute_id")
    private Integer tributeId;

    // 70=Unidad
    @JsonProperty("unit_measure_id")
    private Integer unitMeasureId = 70;

    // 1=Estándar DIAN
    @JsonProperty("standard_code_id")
    private Integer standardCodeId = 1;

    // 0=gravado, 1=excluido
    @JsonProperty("is_excluded")
    private Integer isExcluded = 0;
}
