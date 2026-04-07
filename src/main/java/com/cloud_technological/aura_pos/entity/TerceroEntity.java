package com.cloud_technological.aura_pos.entity;

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

    private String nombres;
    private String apellidos;
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

    @Column(name = "gran_contribuyente")
    private Boolean granContribuyente;

    @Column(name = "auto_retenedor")
    private Boolean autoRetenedor;

    @Column(name = "codigo_ciiu")
    private String codigoCIIU;

    @Column(name = "actividad_economica")
    private String actividadEconomica;

    private String pais;

    @Column(name = "codigo_pais")
    private String codigoPais;

    @Column(name = "es_cliente")
    private Boolean esCliente;

    @Column(name = "es_proveedor")
    private Boolean esProveedor;

    @Column(name = "es_empleado")
    private Boolean esEmpleado;

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
	}

	@PreUpdate
	protected void onUpdate() {
		updated_at = LocalDateTime.now();
	}
    @PreRemove
	public void onDelete() {
		this.deleted_at = LocalDateTime.now();
	}
}

