package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Catálogo GLOBAL de tipos de cotizante PILA (Anexo Técnico 2, Res. 2388/2016).
 *
 * <p>Trae las obligaciones por subsistema: se usa para validar la validez del
 * tipo de cotizante y para exigir EPS/AFP/ARL/CCF solo cuando corresponde.
 * Subconjunto inicial en V123; completar con el catálogo oficial del período.
 */
@Getter
@Setter
@Entity
@Table(name = "pila_tipo_cotizante")
public class PilaTipoCotizanteEntity {

    @Id
    @Column(name = "codigo", length = 5)
    private String codigo;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "oblig_salud", nullable = false)
    private Boolean obligSalud = Boolean.TRUE;

    @Column(name = "oblig_pension", nullable = false)
    private Boolean obligPension = Boolean.TRUE;

    @Column(name = "oblig_arl", nullable = false)
    private Boolean obligArl = Boolean.TRUE;

    @Column(name = "oblig_ccf", nullable = false)
    private Boolean obligCcf = Boolean.TRUE;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "vigencia_desde")
    private LocalDate vigenciaDesde;

    @Column(name = "vigencia_hasta")
    private LocalDate vigenciaHasta;
}
