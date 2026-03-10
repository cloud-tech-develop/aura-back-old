package com.cloud_technological.aura_pos.dto.factus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactusBillResponseDto {
    private String status;
    private String message;
    private FactusBillDataDto data;
}
