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

@Getter
@Setter
@Entity
@Table(name = "nomina_novedad")
public class NominaNovedadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_id")
    private NominaEntity nomina;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    // ── Fechas del hecho (V119) — requeridas por PILA ───────────────────────
    // No son las de la nómina donde se liquida: son las del hecho. Una
    // incapacidad del 28/mar al 5/abr aporta 4 días a marzo y 5 a abril.

    @Column(name = "fecha_inicio")
    private java.time.LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private java.time.LocalDate fechaFin;

    /**
     * Discrimina dentro de un tipo (V119).
     *
     * <p>Para INCAPACIDAD: {@code GENERAL} (la paga la EPS, bandera {@code ige})
     * o {@code RIESGO_LABORAL} (la paga la ARL, bandera {@code irl}). PILA las
     * reporta distinto y con IBC distinto.
     */
    @Column(name = "subtipo", length = 30)
    private String subtipo;

    /** Número de autorización de la incapacidad/licencia. Lo exige la UGPP. */
    @Column(name = "numero_autorizacion", length = 30)
    private String numeroAutorizacion;
    // HORA_EXTRA_DIURNA | HORA_EXTRA_NOCTURNA | HORA_EXTRA_DOMINICAL | HORA_EXTRA_FESTIVO
    // INCAPACIDAD | LICENCIA_REMUNERADA | BONO | COMISION
    // PRESTAMO | EMBARGO | OTRO_DEVENGO | OTRO_DESCUENTO

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    /**
     * Días que abarca la novedad (F0). Para las de ausencia (incapacidad,
     * licencia no remunerada, vacaciones) se deriva del rango de fechas y sirve
     * para descontar del salario proporcional. Null/0 para las que no aplican.
     */
    @Column(name = "dias")
    private Integer dias;

    /**
     * ¿Sus días se restan del salario proporcional? (F0)
     *
     * <p>TRUE en ausencias no pagadas como salario ordinario: incapacidad,
     * licencia no remunerada, vacaciones, licencia de maternidad. FALSE en horas
     * extra, bonos, licencia remunerada, etc. — que no reducen días de salario.
     */
    @Column(name = "afecta_dias_salario", nullable = false)
    private Boolean afectaDiasSalario = false;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "es_deduccion", nullable = false)
    private Boolean esDeduccion = false;

    /**
     * ¿El valor entra en la base de seguridad social? (V106)
     *
     * <p>Corrige el bug B2: antes el motor sumaba TODAS las novedades
     * no-deducción a la base por igual. Pero horas extra y comisiones SÍ son
     * base, un bono no salarial NO, y las incapacidades tienen tratamiento propio.
     *
     * <p>Default TRUE (lo salarial es la regla); V106 marca en FALSE los tipos
     * que no lo son.
     */
    @Column(name = "constituye_ibc", nullable = false)
    private Boolean constituyeIbc = true;

    @Column(name = "naturaleza", length = 20, nullable = false)
    private String naturaleza = "DEVENGADO";
    // DEVENGADO | DEDUCCION | INFORMATIVO | PROVISION | APORTE_EMPLEADOR

    @Column(name = "origen", length = 20, nullable = false)
    private String origen = "MANUAL";
    // MANUAL | ASISTENCIA | IMPORTACION | AJUSTE_ADMIN | RELIQUIDACION | SISTEMA

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "APLICADA";
    // PENDIENTE | APROBADA | RECHAZADA | APLICADA | ANULADA

    @Column(name = "requiere_aprobacion", nullable = false)
    private Boolean requiereAprobacion = false;
}
