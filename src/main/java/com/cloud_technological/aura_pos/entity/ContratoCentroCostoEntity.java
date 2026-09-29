package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

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

/**
 * Distribución del costo laboral por centro de costo (V102).
 *
 * <p>Es el <b>paso 2</b> de la cascada de distribución de la Fase 4.a: se usa
 * cuando el empleado NO tiene asistencia por frente en el período. Si la tiene,
 * gana el reparto por horas reales, que es mejor dato que un porcentaje pactado.
 *
 * <p>Opcional: quien no maneja centros de costo simplemente no crea filas y la
 * cascada cae al paso 3 (una sola fila, sin dimensiones).
 *
 * <p><b>Invariante:</b> los porcentajes de un contrato deben sumar 100. No se
 * puede validar con un CHECK —no puede mirar otras filas— así que va en
 * ContratoLaboralService.
 */
@Getter
@Setter
@Entity
@Table(name = "contrato_centro_costo")
public class ContratoCentroCostoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    @Column(name = "centro_costo_id", nullable = false)
    private Long centroCostoId;

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;
}
