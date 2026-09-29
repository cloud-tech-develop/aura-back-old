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
 * Encabezado de PILA: datos del aportante (V111).
 *
 * <p>Todos los campos del aportante son un <b>snapshot</b> al momento de
 * generar. Una planilla presentada es un hecho del pasado: si la empresa cambia
 * de dirección, la planilla de marzo debe seguir mostrando la de marzo.
 */
@Getter
@Setter
@Entity
@Table(name = "pila_encabezado")
public class PilaEncabezadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** 'YYYY-MM' */
    @Column(name = "periodo", length = 7, nullable = false)
    private String periodo;

    @Column(name = "razon_social", length = 200, nullable = false)
    private String razonSocial;

    @Column(name = "tipo_documento", length = 5, nullable = false)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 30, nullable = false)
    private String numeroDocumento;

    @Column(name = "digito_verificacion", length = 2)
    private String digitoVerificacion;

    @Column(name = "tipo_aportante", length = 5)
    private String tipoAportante;

    @Column(name = "clase_aportante", length = 5)
    private String claseAportante;

    @Column(name = "naturaleza_aportante", length = 5)
    private String naturalezaAportante;

    @Column(name = "tipo_persona", length = 5)
    private String tipoPersona;

    @Column(name = "forma_presentacion", length = 5)
    private String formaPresentacion;

    @Column(name = "codigo_operador", length = 10)
    private String codigoOperador;

    @Column(name = "cod_sucursal", length = 20)
    private String codSucursal;

    @Column(name = "nombre_sucursal", length = 150)
    private String nombreSucursal;

    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "cod_departamento", length = 5)
    private String codDepartamento;

    @Column(name = "cod_ciudad", length = 5)
    private String codCiudad;

    @Column(name = "cod_actividad_economica", length = 10)
    private String codActividadEconomica;

    @Column(name = "telefono", length = 40)
    private String telefono;

    @Column(name = "email", length = 150)
    private String email;

    // Representante legal: obligatorio y desagregado.
    @Column(name = "rep_legal_tipo_documento", length = 5)
    private String repLegalTipoDocumento;

    @Column(name = "rep_legal_documento", length = 30)
    private String repLegalDocumento;

    @Column(name = "rep_legal_apellido1", length = 40)
    private String repLegalApellido1;

    @Column(name = "rep_legal_apellido2", length = 40)
    private String repLegalApellido2;

    @Column(name = "rep_legal_nombre1", length = 40)
    private String repLegalNombre1;

    @Column(name = "rep_legal_nombre2", length = 40)
    private String repLegalNombre2;

    @Column(name = "exonerado_ley_1607", nullable = false)
    private Boolean exoneradoLey1607 = Boolean.FALSE;

    @Column(name = "beneficiario_ley_1429", nullable = false)
    private Boolean beneficiarioLey1429 = Boolean.FALSE;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "BORRADOR";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public static final class Estado {
        public static final String BORRADOR   = "BORRADOR";
        public static final String GENERADA   = "GENERADA";
        public static final String PRESENTADA = "PRESENTADA";
        public static final String PAGADA     = "PAGADA";
        public static final String ANULADA    = "ANULADA";
        private Estado() {}
    }
}
