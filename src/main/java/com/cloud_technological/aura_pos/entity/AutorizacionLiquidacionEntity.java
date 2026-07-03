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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "autorizacion_liquidacion_excepcional")
public class AutorizacionLiquidacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_nomina_id", nullable = false)
    private PeriodoNominaEntity periodoNomina;

    @Column(name = "usuario_autoriza")
    private Integer usuarioAutoriza;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    @Column(name = "motivo", length = 40, nullable = false)
    private String motivo;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "ACTIVA"; // ACTIVA | ANULADA
}
