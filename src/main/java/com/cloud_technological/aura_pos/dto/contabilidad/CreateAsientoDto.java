package com.cloud_technological.aura_pos.dto.contabilidad;

import java.time.LocalDate;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateAsientoDto {

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String descripcion;

    @NotEmpty
    @Valid
    private List<CreateAsientoDetalleDto> detalles;
}
