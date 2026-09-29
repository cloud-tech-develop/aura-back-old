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
 * Registro tipo 02 de PILA: una fila por cotizante (V111).
 *
 * <h2>Tres cosas que hay que entender</h2>
 * <ol>
 *   <li><b>Los IBC son distintos entre subsistemas.</b> No es una base por
 *       cuatro tarifas: alguien en licencia no remunerada cotiza salud pero no
 *       pensión; con incapacidad, el IBC de ARL difiere del de EPS.</li>
 *   <li><b>Los días también son distintos.</b> {@code diasCotizadosSalud} ≠
 *       {@code diasCotizadosPension} según las novedades del mes.</li>
 *   <li><b>Las banderas son derivadas, no capturadas.</b> Se calculan cruzando
 *       contrato + historial salarial + afiliaciones + novedades contra el
 *       período. Ver {@code ResolverNovedadesPila}.</li>
 * </ol>
 *
 * <p>Los códigos de entidad son <b>literales, no FK</b>: una planilla radicada
 * es un hecho del pasado. Si la EPS cambia de código el año que viene, esta
 * planilla debe seguir mostrando el código con el que se radicó.
 */
@Getter
@Setter
@Entity
@Table(name = "pila_cotizante")
public class PilaCotizanteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pila_planilla_id", nullable = false)
    private PilaPlanillaEntity planilla;

    /** Trazabilidad interna. No va al archivo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private ContratoLaboralEntity contrato;

    @Column(name = "tipo_registro", length = 2, nullable = false)
    private String tipoRegistro = "02";

    @Column(name = "secuencia", nullable = false)
    private Integer secuencia;

    // ── Identificación (Fase 1) ─────────────────────────────────────────────

    @Column(name = "tipo_documento", length = 5, nullable = false)
    private String tipoDocumento;

    @Column(name = "numero_identificacion", length = 30, nullable = false)
    private String numeroIdentificacion;

    @Column(name = "tipo_cotizante", length = 5, nullable = false)
    private String tipoCotizante;

    @Column(name = "subtipo_cotizante", length = 5)
    private String subtipoCotizante;

    @Column(name = "extranjero_no_obligado", nullable = false)
    private Boolean extranjeroNoObligado = Boolean.FALSE;

    @Column(name = "colombiano_exterior", nullable = false)
    private Boolean colombianoExterior = Boolean.FALSE;

    @Column(name = "cod_departamento", length = 5)
    private String codDepartamento;

    @Column(name = "cod_ciudad", length = 5)
    private String codCiudad;

    @Column(name = "apellido1", length = 40, nullable = false)
    private String apellido1;

    @Column(name = "apellido2", length = 40)
    private String apellido2;

    @Column(name = "nombre1", length = 40, nullable = false)
    private String nombre1;

    @Column(name = "nombre2", length = 40)
    private String nombre2;

    // ── Banderas de novedad (DERIVADAS) ─────────────────────────────────────

    @Column(name = "ing", nullable = false)        private Boolean ing = false;
    @Column(name = "ret", nullable = false)        private Boolean ret = false;
    @Column(name = "tde", nullable = false)        private Boolean tde = false;
    @Column(name = "tae", nullable = false)        private Boolean tae = false;
    @Column(name = "tdp", nullable = false)        private Boolean tdp = false;
    @Column(name = "tap", nullable = false)        private Boolean tap = false;
    @Column(name = "tdl", nullable = false)        private Boolean tdl = false;
    @Column(name = "tal", nullable = false)        private Boolean tal = false;
    @Column(name = "tie", nullable = false)        private Boolean tie = false;
    @Column(name = "tdc", nullable = false)        private Boolean tdc = false;
    @Column(name = "tac", nullable = false)        private Boolean tac = false;
    @Column(name = "vsp", nullable = false)        private Boolean vsp = false;
    @Column(name = "correccion", nullable = false) private Boolean correccion = false;
    @Column(name = "vst", nullable = false)        private Boolean vst = false;
    @Column(name = "sln", nullable = false)        private Boolean sln = false;
    @Column(name = "ige", nullable = false)        private Boolean ige = false;
    @Column(name = "lma", nullable = false)        private Boolean lma = false;
    @Column(name = "vac_lr", nullable = false)     private Boolean vacLr = false;
    @Column(name = "avp", nullable = false)        private Boolean avp = false;
    @Column(name = "vct", nullable = false)        private Boolean vct = false;
    @Column(name = "irl", nullable = false)        private Boolean irl = false;

    // ── Fechas de las novedades ─────────────────────────────────────────────

    @Column(name = "fecha_ingreso")       private LocalDate fechaIngreso;
    @Column(name = "fecha_retiro")        private LocalDate fechaRetiro;
    @Column(name = "fecha_inicio_vsp")    private LocalDate fechaInicioVsp;
    @Column(name = "fecha_inicio_vst")    private LocalDate fechaInicioVst;
    @Column(name = "fecha_fin_vst")       private LocalDate fechaFinVst;
    @Column(name = "fecha_inicio_sln")    private LocalDate fechaInicioSln;
    @Column(name = "fecha_fin_sln")       private LocalDate fechaFinSln;
    @Column(name = "fecha_inicio_ige")    private LocalDate fechaInicioIge;
    @Column(name = "fecha_fin_ige")       private LocalDate fechaFinIge;
    @Column(name = "fecha_inicio_lma")    private LocalDate fechaInicioLma;
    @Column(name = "fecha_fin_lma")       private LocalDate fechaFinLma;
    @Column(name = "fecha_inicio_vac_lr") private LocalDate fechaInicioVacLr;
    @Column(name = "fecha_fin_vac_lr")    private LocalDate fechaFinVacLr;
    @Column(name = "fecha_inicio_vct")    private LocalDate fechaInicioVct;
    @Column(name = "fecha_fin_vct")       private LocalDate fechaFinVct;
    @Column(name = "fecha_inicio_irl")    private LocalDate fechaInicioIrl;
    @Column(name = "fecha_fin_irl")       private LocalDate fechaFinIrl;

    // ── Entidades: códigos LITERALES (snapshot) ─────────────────────────────

    @Column(name = "cod_eps", length = 10)          private String codEps;
    @Column(name = "cod_eps_traslado", length = 10) private String codEpsTraslado;
    @Column(name = "cod_afp", length = 10)          private String codAfp;
    @Column(name = "cod_afp_traslado", length = 10) private String codAfpTraslado;
    @Column(name = "cod_ccf", length = 10)          private String codCcf;
    @Column(name = "cod_arl", length = 10)          private String codArl;
    @Column(name = "clase_riesgo", length = 2)      private String claseRiesgo;
    @Column(name = "centro_trabajo", length = 20)   private String centroTrabajo;

    // ── Días por subsistema — SON DISTINTOS ENTRE SÍ ────────────────────────

    @Column(name = "dias_cotizados_pension", nullable = false) private Integer diasCotizadosPension = 0;
    @Column(name = "dias_cotizados_salud", nullable = false)   private Integer diasCotizadosSalud = 0;
    @Column(name = "dias_cotizados_arl", nullable = false)     private Integer diasCotizadosArl = 0;
    @Column(name = "dias_cotizados_ccf", nullable = false)     private Integer diasCotizadosCcf = 0;

    // ── IBC por subsistema — TAMBIÉN SON DISTINTOS ──────────────────────────

    @Column(name = "salario_basico", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioBasico = BigDecimal.ZERO;

    @Column(name = "salario_integral", nullable = false)
    private Boolean salarioIntegral = Boolean.FALSE;

    @Column(name = "ibc_pension", nullable = false, precision = 15, scale = 2)
    private BigDecimal ibcPension = BigDecimal.ZERO;

    @Column(name = "ibc_salud", nullable = false, precision = 15, scale = 2)
    private BigDecimal ibcSalud = BigDecimal.ZERO;

    @Column(name = "ibc_arl", nullable = false, precision = 15, scale = 2)
    private BigDecimal ibcArl = BigDecimal.ZERO;

    @Column(name = "ibc_ccf", nullable = false, precision = 15, scale = 2)
    private BigDecimal ibcCcf = BigDecimal.ZERO;

    @Column(name = "ibc_otros_parafiscales", nullable = false, precision = 15, scale = 2)
    private BigDecimal ibcOtrosParafiscales = BigDecimal.ZERO;

    // ── Pensión ─────────────────────────────────────────────────────────────

    @Column(name = "tarifa_pension", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaPension = BigDecimal.ZERO;

    @Column(name = "aporte_pension", nullable = false, precision = 15, scale = 2)
    private BigDecimal aportePension = BigDecimal.ZERO;

    @Column(name = "aporte_voluntario_afiliado", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteVoluntarioAfiliado = BigDecimal.ZERO;

    @Column(name = "aporte_voluntario_aportante", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteVoluntarioAportante = BigDecimal.ZERO;

    @Column(name = "total_pension", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPension = BigDecimal.ZERO;

    @Column(name = "aporte_fondo_solidaridad", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteFondoSolidaridad = BigDecimal.ZERO;

    @Column(name = "aporte_fondo_subsistencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteFondoSubsistencia = BigDecimal.ZERO;

    @Column(name = "valor_no_retenido", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorNoRetenido = BigDecimal.ZERO;

    @Column(name = "indicador_tarifa_pension", length = 2)
    private String indicadorTarifaPension;

    // ── Salud ───────────────────────────────────────────────────────────────

    @Column(name = "tarifa_salud", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaSalud = BigDecimal.ZERO;

    @Column(name = "aporte_salud", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteSalud = BigDecimal.ZERO;

    @Column(name = "valor_upc", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUpc = BigDecimal.ZERO;

    @Column(name = "no_autorizacion_ige", length = 30)
    private String noAutorizacionIge;

    @Column(name = "valor_ige", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorIge = BigDecimal.ZERO;

    @Column(name = "no_autorizacion_lma", length = 30)
    private String noAutorizacionLma;

    @Column(name = "valor_lma", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorLma = BigDecimal.ZERO;

    // ── ARL ─────────────────────────────────────────────────────────────────

    @Column(name = "tarifa_riesgos", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaRiesgos = BigDecimal.ZERO;

    @Column(name = "aporte_riesgos", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteRiesgos = BigDecimal.ZERO;

    // ── Parafiscales ────────────────────────────────────────────────────────

    @Column(name = "tarifa_ccf", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaCcf = BigDecimal.ZERO;

    @Column(name = "aporte_ccf", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteCcf = BigDecimal.ZERO;

    @Column(name = "tarifa_sena", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaSena = BigDecimal.ZERO;

    @Column(name = "aporte_sena", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteSena = BigDecimal.ZERO;

    @Column(name = "tarifa_icbf", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaIcbf = BigDecimal.ZERO;

    @Column(name = "aporte_icbf", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteIcbf = BigDecimal.ZERO;

    @Column(name = "tarifa_esap", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaEsap = BigDecimal.ZERO;

    @Column(name = "aporte_esap", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteEsap = BigDecimal.ZERO;

    @Column(name = "tarifa_men", nullable = false, precision = 6, scale = 3)
    private BigDecimal tarifaMen = BigDecimal.ZERO;

    @Column(name = "aporte_men", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteMen = BigDecimal.ZERO;

    @Column(name = "exonerado_ley_1607", nullable = false)
    private Boolean exoneradoLey1607 = Boolean.FALSE;

    @Column(name = "horas_laboradas")
    private Integer horasLaboradas;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
