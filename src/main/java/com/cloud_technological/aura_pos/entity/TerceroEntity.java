package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tercero")
@Getter @Setter
@Builder
@NoArgsConstructor  // ✅
@AllArgsConstructor // ✅
public class TerceroEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(name = "tipo_documento")
    private String tipoDocumento;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    private String dv;

    @Column(name = "razon_social")
    private String razonSocial;

    /** @deprecated Usar {@link #nombre1}/{@link #nombre2}. DIAN y UGPP exigen desagregado. */
    @Deprecated
    private String nombres;

    /** @deprecated Usar {@link #apellido1}/{@link #apellido2}. */
    @Deprecated
    private String apellidos;

    // ── Identificación desagregada (V97) ────────────────────────────
    // DIAN (nómina electrónica) y UGPP (registro tipo 02 de PILA) exigen
    // los cuatro componentes POR SEPARADO. Partirlos en tiempo de envío
    // por heurística falla con "DE LA ROSA", nombres de una palabra, etc.

    @Column(name = "nombre1", length = 40)
    private String nombre1;

    @Column(name = "nombre2", length = 40)
    private String nombre2;

    @Column(name = "apellido1", length = 40)
    private String apellido1;

    @Column(name = "apellido2", length = 40)
    private String apellido2;

    private String direccion;
    private String telefono;
    private String email;
    private String municipio;
    private Long municipioId;
    @Column(name = "email_fe")
    private String emailFe;

    @Column(name = "responsabilidad_fiscal")
    private String responsabilidadFiscal;

    // ── Campos fiscales (V52) ───────────────────────────────────
    @Column(name = "tipo_persona")
    private String tipoPersona;

    private String regimen;

    // NOT NULL en BD (V52). Sin default aqui, cualquier alta que no mande el
    // campo (p.ej. crear usuario) inserta NULL y revienta la constraint.
    @Column(name = "gran_contribuyente", nullable = false)
    @lombok.Builder.Default
    private Boolean granContribuyente = Boolean.FALSE;

    @Column(name = "auto_retenedor", nullable = false)
    @lombok.Builder.Default
    private Boolean autoRetenedor = Boolean.FALSE;

    @Column(name = "codigo_ciiu")
    private String codigoCIIU;

    @Column(name = "actividad_economica")
    private String actividadEconomica;

    private String pais;

    @Column(name = "codigo_pais")
    private String codigoPais;

    // ── Persona natural (V97) ───────────────────────────────────────

    /** Requerido por PILA. */
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    /** M | F | OTRO. Requerido por PILA. */
    @Column(name = "sexo", length = 10)
    private String sexo;

    @Column(name = "fecha_expedicion_documento")
    private LocalDate fechaExpedicionDocumento;

    @Column(name = "municipio_expedicion_id")
    private Long municipioExpedicionId;

    // ── Persona jurídica (V97) ──────────────────────────────────────

    /** Marca comercial, distinta de la razón social. */
    @Column(name = "nombre_comercial", length = 150)
    private String nombreComercial;

    /** Obligatorio en el encabezado de PILA cuando la empresa es el aportante. */
    @Column(name = "representante_legal_nombre", length = 150)
    private String representanteLegalNombre;

    @Column(name = "representante_legal_documento", length = 30)
    private String representanteLegalDocumento;

    // ── Fiscal (V97) ────────────────────────────────────────────────
    // `autoRetenedor` es un solo boolean, pero son autorretenciones distintas:
    // se puede ser de renta y no de ICA.

    @Column(name = "es_autoretenedor_ica", nullable = false)
    @lombok.Builder.Default
    private Boolean esAutoretenedorIca = Boolean.FALSE;

    @Column(name = "es_autoretenedor_fuente", nullable = false)
    @lombok.Builder.Default
    private Boolean esAutoretenedorFuente = Boolean.FALSE;

    @Column(name = "declarante", nullable = false)
    @lombok.Builder.Default
    private Boolean declarante = Boolean.FALSE;

    // ── Bancario (V97) ──────────────────────────────────────────────
    // Movido desde `empleados`: al pasar la identidad a tercero, allá quedaba
    // huérfano. `bancoTerceroId` es la FK real (V101); `banco` es el texto
    // migrado, en retiro.

    /** @deprecated Usar {@link #bancoTerceroId}. Texto libre — no confiable. */
    @Deprecated
    @Column(name = "banco", length = 100)
    private String banco;

    /** Entidad financiera. FK a un tercero con rol BANCO. */
    @Column(name = "banco_tercero_id")
    private Long bancoTerceroId;

    @Column(name = "tipo_cuenta", length = 20)
    private String tipoCuenta;

    @Column(name = "numero_cuenta", length = 50)
    private String numeroCuenta;

    /**
     * Enlace al catálogo nacional (V110). Solo para terceros con rol
     * EPS/AFP/CCF/ARL: de aquí sale el código oficial para PILA.
     *
     * <p>El cliente elige de una lista, no digita el código — así no divergen
     * entre empresas y el archivo no se rechaza.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entidad_seguridad_social_id")
    private EntidadSeguridadSocialEntity entidadSeguridadSocial;

    /**
     * Código oficial UGPP (V120). Se llena cuando el tercero es EPS/AFP/CCF/ARL
     * y es lo que PILA usa para identificar a la entidad. Reemplaza al enlace de
     * catálogo para el flujo de "crear la entidad como tercero".
     */
    @Column(name = "codigo_seguridad_social", length = 20)
    private String codigoSeguridadSocial;

    @Column(name = "es_cliente")
    private Boolean esCliente;

    @Column(name = "es_proveedor")
    private Boolean esProveedor;

    @Column(name = "es_empleado")
    private Boolean esEmpleado;

    /** Entidad financiera / banco. Exclusivo: no tiene rol comercial. */
    @Column(name = "es_banco")
    private Boolean esBanco;

    private Boolean activo;

    @Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime created_at;

	@Column(name = "updated_at")
	private LocalDateTime updated_at;

    @Column(name = "deleted_at")
    private LocalDateTime deleted_at;

    @PrePersist
	protected void onCreate() {
		created_at = LocalDateTime.now();
		normalizarBooleanosObligatorios();
	}

	/**
	 * Las columnas booleanas NOT NULL de V52/V97 tienen DEFAULT en BD, pero el
	 * default no aplica cuando Hibernate manda NULL explicito en el INSERT.
	 */
	private void normalizarBooleanosObligatorios() {
		if (granContribuyente == null) granContribuyente = Boolean.FALSE;
		if (autoRetenedor == null) autoRetenedor = Boolean.FALSE;
		if (esAutoretenedorIca == null) esAutoretenedorIca = Boolean.FALSE;
		if (esAutoretenedorFuente == null) esAutoretenedorFuente = Boolean.FALSE;
		if (declarante == null) declarante = Boolean.FALSE;
	}

	@PreUpdate
	protected void onUpdate() {
		updated_at = LocalDateTime.now();
		normalizarBooleanosObligatorios();
	}
    @PreRemove
	public void onDelete() {
		this.deleted_at = LocalDateTime.now();
	}
}

