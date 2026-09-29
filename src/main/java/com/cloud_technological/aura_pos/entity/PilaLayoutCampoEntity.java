package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Definición de un campo del archivo PILA (P6), dirigida por datos. El
 * exportador recorre el layout por {@code orden} y resuelve cada {@code codigo}
 * del modelo. Editable → se ajusta al layout oficial sin recompilar.
 */
@Getter
@Setter
@Entity
@Table(name = "pila_layout_campo")
public class PilaLayoutCampoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** '01' encabezado | '02' cotizante */
    @Column(name = "tipo_registro", length = 2, nullable = false)
    private String tipoRegistro;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "codigo", length = 40, nullable = false)
    private String codigo;

    @Column(name = "etiqueta", length = 120)
    private String etiqueta;

    @Column(name = "longitud")
    private Integer longitud;

    /** N (numérico) | A (alfanumérico) */
    @Column(name = "tipo_dato", length = 1, nullable = false)
    private String tipoDato = "A";

    @Column(name = "relleno", length = 1)
    private String relleno;

    /** I (izquierda) | D (derecha) */
    @Column(name = "alineacion", length = 1, nullable = false)
    private String alineacion = "D";

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;
}
