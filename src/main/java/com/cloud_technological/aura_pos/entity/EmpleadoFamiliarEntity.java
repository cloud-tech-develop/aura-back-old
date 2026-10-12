package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Familiar del empleado: núcleo familiar, beneficiarios y contacto de emergencia (V213). */
@Entity
@Table(name = "empleado_familiar")
@Getter
@Setter
public class EmpleadoFamiliarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    /** CONYUGE | COMPANERO | HIJO | HIJASTRO | PADRE | MADRE | HERMANO | ABUELO | NIETO | OTRO. */
    @Column(nullable = false, length = 20)
    private String parentesco;

    @Column(name = "tipo_documento", length = 10)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 30)
    private String numeroDocumento;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(length = 10)
    private String genero;

    @Column(length = 30)
    private String telefono;

    @Column(length = 100)
    private String ocupacion;

    @Column(nullable = false)
    private Boolean convive = Boolean.FALSE;

    @Column(name = "depende_economicamente", nullable = false)
    private Boolean dependeEconomicamente = Boolean.FALSE;

    /** Beneficiario de la caja de compensación (cuota monetaria). */
    @Column(name = "beneficiario_caja", nullable = false)
    private Boolean beneficiarioCaja = Boolean.FALSE;

    /** Beneficiario en la EPS del empleado. */
    @Column(name = "beneficiario_salud", nullable = false)
    private Boolean beneficiarioSalud = Boolean.FALSE;

    @Column(name = "contacto_emergencia", nullable = false)
    private Boolean contactoEmergencia = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
