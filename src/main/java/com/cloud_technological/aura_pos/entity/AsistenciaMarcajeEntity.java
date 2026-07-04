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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "asistencia_marcaje")
public class AsistenciaMarcajeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_hora_marcaje", nullable = false)
    private LocalDateTime fechaHoraMarcaje;

    @Column(name = "tipo_marcaje", length = 20, nullable = false)
    private String tipoMarcaje; // ENTRADA | SALIDA | INICIO_DESCANSO | FIN_DESCANSO

    @Column(name = "origen_marcaje", length = 20, nullable = false)
    private String origenMarcaje = "ASISTENTE";

    @Column(name = "registrado_por")
    private Integer registradoPor;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "evidencia_url", length = 255)
    private String evidenciaUrl;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "VALIDO"; // VALIDO | PENDIENTE_REVISION | ANULADO | CORREGIDO

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
