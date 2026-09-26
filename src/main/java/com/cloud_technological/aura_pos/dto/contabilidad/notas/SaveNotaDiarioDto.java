package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crea o edita una nota contable en borrador. */
@Getter @Setter
public class SaveNotaDiarioDto {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "El concepto es obligatorio")
    @Size(max = 500, message = "El concepto admite máximo 500 caracteres")
    private String descripcion;

    @NotEmpty(message = "La nota debe tener al menos una línea")
    @Valid
    private List<SaveNotaDiarioLineaDto> lineas;

    /**
     * true = guardar y contabilizar en el mismo paso. Si la nota no pasa las
     * validaciones de contabilización no se guarda nada (transaccional).
     */
    private Boolean contabilizar = Boolean.FALSE;

    /** AJUSTE | RECLASIFICACION | PROVISION | CAUSACION | DEPRECIACION | CORRECCION | OTRO */
    private String clasificacion;

    /** Al contabilizar, generar también la reversión el día 1 del mes siguiente. */
    private Boolean reversionAutomatica = Boolean.FALSE;

    /** Plantilla de la que sale la nota (solo al crear). */
    private Long plantillaId;
}
