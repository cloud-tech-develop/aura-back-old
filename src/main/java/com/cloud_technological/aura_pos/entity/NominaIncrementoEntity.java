package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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

/** Incremento salarial (V221, rediseño R8): encabezado del lote. */
@Entity
@Table(name = "nomina_incremento")
@Getter
@Setter
public class NominaIncrementoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer numero;

    /** EMPLEADO | VINCULACION | GRUPO | TIPO_VINCULACION | TODOS. */
    @Column(name = "tipo_lista", nullable = false, length = 20)
    private String tipoLista;

    /** Ids elegidos separados por coma (vacío en TODOS). */
    @Column(name = "lista_ids")
    private String listaIds;

    /** PORCENTAJE | VALOR_ADICIONAL | NUEVO_VALOR | SMMLV. */
    @Column(name = "tipo_incremento", nullable = false, length = 20)
    private String tipoIncremento;

    @Column(precision = 15, scale = 4)
    private BigDecimal valor;

    /** NINGUNO | PESO | CENTENA | CENTENA_ARRIBA | CENTENA_ABAJO | MIL. */
    @Column(nullable = false, length = 20)
    private String redondeo = "NINGUNO";

    @Column(name = "fecha_aplicacion", nullable = false)
    private LocalDate fechaAplicacion;

    @Column(length = 300)
    private String descripcion;

    /** PENDIENTE | APLICADO | REVERSADO | ANULADO. */
    @Column(nullable = false, length = 12)
    private String estado = "PENDIENTE";

    /** Prenómina de retroactivo generada desde el incremento. */
    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "aplicado_at")
    private LocalDateTime aplicadoAt;

    @Column(name = "reversado_at")
    private LocalDateTime reversadoAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
