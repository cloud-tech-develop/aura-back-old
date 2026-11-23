package com.cloud_technological.aura_pos.entity.servicio_tecnico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Orden de servicio técnico: cliente, técnico, estado, diagnóstico y totales (V249). */
@Entity
@Table(name = "servicio_orden")
@Getter
@Setter
@NoArgsConstructor
public class ServicioOrdenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(nullable = false, updatable = false)
    private Integer numero;

    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "contacto_telefono", length = 30)
    private String contactoTelefono;

    @Column(name = "contacto_email", length = 150)
    private String contactoEmail;

    @Column(name = "tecnico_id")
    private Long tecnicoId;

    @Column(nullable = false, length = 25)
    private String estado = "RECIBIDO";

    @Column(nullable = false, length = 10)
    private String prioridad = "NORMAL";

    @Column(name = "fecha_recibido", nullable = false)
    private LocalDateTime fechaRecibido;

    @Column(name = "fecha_estimada")
    private LocalDate fechaEstimada;

    @Column(name = "fecha_entregado")
    private LocalDateTime fechaEntregado;

    @Column(columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "trabajo_realizado", columnDefinition = "TEXT")
    private String trabajoRealizado;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "garantia_dias", nullable = false)
    private Integer garantiaDias = 0;

    @Column(name = "total_repuestos", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalRepuestos = BigDecimal.ZERO;

    @Column(name = "total_mano_obra", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalManoObra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "total_abonado", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAbonado = BigDecimal.ZERO;

    /** Factura de venta con la que se cobró (fase 2). */
    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    /** Factura de venta (borrador o emitida) con la que se entregó (V250). */
    @Column(name = "factura_venta_id")
    private Long facturaVentaId;

    /** Clave del enlace público de consulta; nunca se muestra la orden sin ella (V250). */
    @Column(name = "token_publico", length = 40)
    private String tokenPublico;

    /** Cuándo aprobó el cliente el presupuesto desde el enlace (V250). */
    @Column(name = "aprobado_cliente_at")
    private LocalDateTime aprobadoClienteAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
