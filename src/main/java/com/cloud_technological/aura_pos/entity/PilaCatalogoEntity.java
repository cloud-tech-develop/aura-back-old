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
 * Catálogo GLOBAL clave-valor de códigos simples de PILA (tipo de planilla,
 * tipo de aportante, etc.), Anexo Técnico 2. Subconjunto inicial en V125.
 */
@Getter
@Setter
@Entity
@Table(name = "pila_catalogo")
public class PilaCatalogoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TIPO_PLANILLA | TIPO_APORTANTE | ... */
    @Column(name = "dominio", length = 30, nullable = false)
    private String dominio;

    @Column(name = "codigo", length = 10, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;
}
