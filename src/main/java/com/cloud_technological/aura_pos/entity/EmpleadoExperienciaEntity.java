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

/** Experiencia laboral del empleado antes de la empresa (V213). */
@Entity
@Table(name = "empleado_experiencia")
@Getter
@Setter
public class EmpleadoExperienciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    @Column(nullable = false, length = 150)
    private String empresa;

    @Column(nullable = false, length = 150)
    private String cargo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** null = sigue trabajando ahí. */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "motivo_retiro", length = 150)
    private String motivoRetiro;

    @Column(name = "jefe_inmediato", length = 150)
    private String jefeInmediato;

    /** Teléfono de referencia. */
    @Column(length = 30)
    private String telefono;

    @Column(length = 1000)
    private String funciones;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
