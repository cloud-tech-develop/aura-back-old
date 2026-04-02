package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gestion_cobro")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GestionCobroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_cobrar_id")
    private CuentaCobrarEntity cuentaCobrar;

    @Column(name = "tipo_gestion", length = 30)
    private String tipoGestion; // LLAMADA | EMAIL | VISITA | NOTA | ACUERDO_PAGO | MENSAJE

    @Column(length = 30)
    private String resultado; // CONTACTADO | NO_CONTESTO | PROMESA_PAGO | RENUENTE | PAGADO

    @Column(columnDefinition = "TEXT")
    private String nota;

    @Column(name = "fecha_promesa_pago")
    private LocalDate fechaPromesaPago;

    @Column(name = "monto_prometido", precision = 15, scale = 2)
    private BigDecimal montoPrometido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
