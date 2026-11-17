package com.cloud_technological.aura_pos.entity.servicio_tecnico;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuración del servicio técnico de una empresa: consecutivo, garantía y condiciones (V249). */
@Entity
@Table(name = "servicio_tecnico_config")
@Getter
@Setter
@NoArgsConstructor
public class ServicioTecnicoConfigEntity {

    @Id
    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(nullable = false, length = 10)
    private String prefijo = "OS";

    @Column(name = "siguiente_numero", nullable = false)
    private Integer siguienteNumero = 1;

    @Column(name = "garantia_dias", nullable = false)
    private Integer garantiaDias = 30;

    /** Condiciones que salen impresas en la orden (abandono, garantía, datos…). */
    @Column(columnDefinition = "TEXT")
    private String condiciones;

    /** Servicio con el que se facturan la mano de obra y los repuestos fuera del inventario (V250). */
    @Column(name = "producto_servicio_id")
    private Long productoServicioId;

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
