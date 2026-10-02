package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Mejora que se capitaliza: sube el costo y opcionalmente la vida útil. */
@Getter
@Setter
public class AdicionActivoDto {
    private Long id;
    private Long activoId;
    private LocalDate fecha;
    private String descripcion;
    private BigDecimal valor;
    private Integer mesesAdicionales;
    /** De dónde salió la plata o a quién se le debe: caja, banco, proveedores… */
    private Long cuentaContrapartidaId;
    private String cuentaContrapartida;
    private Long terceroId;
    private Long asientoId;
}
