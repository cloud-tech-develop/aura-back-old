package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Columna de valor de un formato de exógena, en el orden del prevalidador DIAN
 * (p. ej. 1001: pago deducible, no deducible, IVA, retención practicada…). Es dato,
 * no código: se ajusta cuando la DIAN cambia la versión del formato (V225).
 */
@Entity
@Table(name = "exogena_columna")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaColumnaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "formato_id", nullable = false)
    private Long formatoId;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private Integer orden;

    /** La columna principal: la que va en {@code exogena_linea.valor} y define cuantías menores. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean principal = false;
}
