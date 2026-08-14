package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Catálogo NACIONAL de EPS/AFP/CCF/ARL con su código oficial del operador de
 * PILA (V110).
 *
 * <h2>Por qué es global (sin empresa_id)</h2>
 * {@link TerceroEntity} es multi-tenant, pero los códigos de PILA son
 * nacionales. Si cada empresa creara su tercero "EPS SURA" y digitara el
 * código, divergirían ({@code EPS002}, {@code EPS-002}, vacío) y el archivo se
 * rechazaría por empresa sin diagnóstico claro.
 *
 * <p>Por eso: el cliente <b>elige de una lista</b>, no digita. Y cuando una EPS
 * se fusiona o se liquida —pasa seguido— se actualiza aquí una vez, no empresa
 * por empresa.
 *
 * <p><b>Este catálogo lo mantiene el proveedor, no el cliente.</b> Requiere
 * mantenimiento: no es un seed de una sola vez.
 *
 * <h2>Relación con tercero</h2>
 * A estas entidades <b>se les paga</b>: son acreedores. Por eso existen también
 * como {@link TerceroEntity} con rol EPS/AFP/CCF/ARL (tesorería les gira, el
 * asiento necesita su NIT, exógena las reporta). Esta tabla solo aporta el
 * código oficial; el enlace es {@code tercero.entidad_seguridad_social_id}.
 */
@Getter
@Setter
@Entity
@Table(name = "entidad_seguridad_social")
public class EntidadSeguridadSocialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo", length = 10, nullable = false)
    private String tipo;   // EPS | AFP | CCF | ARL

    /** El que exige el operador de PILA. Es la razón de ser de esta tabla. */
    @Column(name = "codigo_oficial", length = 20, nullable = false)
    private String codigoOficial;

    @Column(name = "nit", length = 30, nullable = false)
    private String nit;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public static final class Tipo {
        public static final String EPS = "EPS";
        public static final String AFP = "AFP";
        public static final String CCF = "CCF";
        public static final String ARL = "ARL";
        private Tipo() {}
    }
}
