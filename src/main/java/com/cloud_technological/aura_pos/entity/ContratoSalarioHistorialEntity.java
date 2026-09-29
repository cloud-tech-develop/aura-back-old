package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Historial salarial de un contrato (V102).
 *
 * <p><b>No es opcional</b>, aunque se descarte el multi-vínculo. Antes un
 * aumento sobrescribía {@code empleados.salario_base} y el valor anterior se
 * perdía. Sin este histórico:
 * <ul>
 *   <li>No se puede liquidar un retroactivo.</li>
 *   <li>No se puede auditar por qué una nómina vieja dio ese número.</li>
 *   <li>PILA no puede calcular la bandera {@code vsp} (variación permanente
 *       de salario), que se deriva de detectar un cambio dentro del período.</li>
 *   <li>La liquidación definitiva no puede promediar el último año.</li>
 * </ul>
 *
 * <p>Invariante: una sola fila con {@code fechaHasta = null} por contrato
 * (índice único {@code ux_csh_vigente}).
 */
@Getter
@Setter
@Entity
@Table(name = "contrato_salario_historial")
public class ContratoSalarioHistorialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    @Column(name = "salario", nullable = false, precision = 15, scale = 2)
    private BigDecimal salario;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    /** NULL = vigente. Solo una fila por contrato puede tenerlo en null. */
    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
