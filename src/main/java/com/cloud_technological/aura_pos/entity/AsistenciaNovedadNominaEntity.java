package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
@Table(name = "asistencia_novedad_nomina")
public class AsistenciaNovedadNominaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_nomina_id")
    private PeriodoNominaEntity periodoNomina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asistencia_dia_id")
    private AsistenciaDiaEntity asistenciaDia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asistencia_incidencia_id")
    private AsistenciaIncidenciaEntity asistenciaIncidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    @Column(name = "tipo_novedad", length = 40, nullable = false)
    private String tipoNovedad;

    @Column(name = "unidad", length = 10, nullable = false)
    private String unidad = "HORAS"; // HORAS | DIAS | MINUTOS | VALOR

    @Column(name = "cantidad", precision = 10, scale = 2, nullable = false)
    private BigDecimal cantidad = BigDecimal.ZERO;

    @Column(name = "valor_manual", precision = 15, scale = 2)
    private BigDecimal valorManual;

    @Column(name = "origen", length = 20, nullable = false)
    private String origen = "ASISTENCIA";

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "PENDIENTE"; // PENDIENTE | APROBADA | RECHAZADA | ENVIADA_A_NOMINA

    @Column(name = "fecha_generacion")
    private LocalDateTime fechaGeneracion;

    @Column(name = "generado_por")
    private Integer generadoPor;
}
