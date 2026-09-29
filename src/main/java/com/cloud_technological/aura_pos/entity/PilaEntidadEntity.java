package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Catálogo GLOBAL de entidades administradoras de PILA (EPS/AFP/ARL/CCF) con su
 * código oficial y vigencia. Se carga del catálogo oficial del período (P2b).
 *
 * <p>Mientras un tipo no tenga filas, el validador no valida ese código: solo
 * la presencia (no se inventan códigos).
 */
@Getter
@Setter
@Entity
@Table(name = "pila_entidad")
public class PilaEntidadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** EPS | AFP | ARL | CCF */
    @Column(name = "tipo", length = 5, nullable = false)
    private String tipo;

    @Column(name = "codigo", length = 10, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "vigencia_desde")
    private LocalDate vigenciaDesde;

    @Column(name = "vigencia_hasta")
    private LocalDate vigenciaHasta;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;
}
