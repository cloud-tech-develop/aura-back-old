package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Base;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Clase;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.FondoSolidaridadRangoEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.repositories.nomina.AcumuladoMesQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.FondoSolidaridadRangoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository.ConceptoPropio;
import com.cloud_technological.aura_pos.services.implementations.ConceptoNominaService;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasAprendiz;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasNovedadesPermitidas;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Motor de liquidación (Fases 0 y 3).
 *
 * <p>Extraído de {@code NominaServiceImpl}, que mezclaba cálculo, orquestación,
 * pago y auditoría en 854 líneas. Aquí solo se calcula.
 *
 * <h2>Qué cambia respecto al motor viejo</h2>
 * <ul>
 *   <li><b>Itera conceptos</b> de {@code concepto_nomina} en vez de tener las
 *       tarifas compiladas ({@code PCT_PRIMA = 8.33}). Un cambio de ley es una
 *       fila, no un despliegue.</li>
 *   <li><b>Separa las tres bases</b> ({@link BasesLiquidacion}). El auxilio de
 *       transporte ya no infla el IBC.</li>
 *   <li><b>Aplica los topes legales</b>: 25 SMMLV, fondo de solidaridad,
 *       exoneración Ley 1607, salario integral.</li>
 *   <li><b>Emite {@code nomina_detalle}</b> con traza, en vez de solo agregados.</li>
 * </ul>
 *
 * <h2>Lo que NO se hizo, a propósito</h2>
 * No hay {@code eval()} de fórmulas almacenadas. {@code concepto.base} es un
 * enum acotado. El ERP de referencia guarda PHP en base64 y lo ejecuta; eso es
 * ejecución de código arbitrario, no se testea y no se versiona.
 */
@Slf4j
@Component
public class MotorLiquidacion {

    private static final BigDecimal CIEN     = new BigDecimal("100");
    private static final BigDecimal TREINTA  = new BigDecimal("30");
    private static final BigDecimal DOS      = new BigDecimal("2");
    private static final int ESCALA = 2;

    /** Conceptos que la exoneración Ley 1607 apaga. NO incluye CCF ni pensión. */
    private static final Set<String> EXONERABLES_1607 = Set.of("APO_SALUD", "APO_SENA", "APO_ICBF");

    private final ConceptoNominaService conceptoService;
    private final FondoSolidaridadRangoJPARepository fondoRepo;
    private final CalculadoraRetefuente calculadoraRetefuente;
    private final DistribuidorCostoLaboral distribuidor;
    private final VinculacionReglasQueryRepository reglasVinculacion;
    private final AcumuladoMesQueryRepository acumuladoRepo;

    public MotorLiquidacion(ConceptoNominaService conceptoService,
                            FondoSolidaridadRangoJPARepository fondoRepo,
                            CalculadoraRetefuente calculadoraRetefuente,
                            DistribuidorCostoLaboral distribuidor,
                            VinculacionReglasQueryRepository reglasVinculacion,
                            AcumuladoMesQueryRepository acumuladoRepo) {
        this.conceptoService = conceptoService;
        this.fondoRepo = fondoRepo;
        this.calculadoraRetefuente = calculadoraRetefuente;
        this.distribuidor = distribuidor;
        this.reglasVinculacion = reglasVinculacion;
        this.acumuladoRepo = acumuladoRepo;
    }

    /**
     * Liquida una nómina.
     *
     * @return las líneas de detalle. El llamador las persiste y agrega los
     *         totales en {@link NominaEntity}.
     */
    public ResultadoLiquidacion liquidar(NominaEntity nomina,
                                         ContratoLaboralEntity contrato,
                                         NominaConfigEntity config,
                                         LocalDate fechaLiquidacion) {

        // R3.2d — las novedades del período deben respetar los topes de la vinculación.
        validarNovedadesPermitidas(nomina, contrato);

        // R3.2d — conceptos propios de la vinculación. Se calculan sobre el salario
        // proporcional (primera pasada) y los devengados salariales entran al IBC.
        // Quincenas: lo ya liquidado en el mes, para las reglas mensuales (tope de IBC,
        // Ley 1393, exoneración, fondo de solidaridad y retención).
        AcumuladoMes acumulado = acumuladoDelMes(nomina, contrato);
        BasesLiquidacion preliminar = calcularBases(nomina, contrato, config, BigDecimal.ZERO, BigDecimal.ZERO,
                acumulado);
        List<LineaLiquidacion> propias = lineasConceptosPropios(nomina, contrato, config, fechaLiquidacion,
                preliminar.getSalarioProporcional());
        BasesLiquidacion bases = calcularBases(nomina, contrato, config,
                devengadoPropio(propias, true), devengadoPropio(propias, false), acumulado);
        boolean modoCompleto = "COMPLETO".equals(config.getModoNomina());
        // Aprendiz SENA (Ley 2466/2025): sin parafiscales ni FSP; en lectiva tampoco
        // descuentos, pensión ni prestaciones. Ver ReglasAprendiz.
        boolean esAprendiz = ReglasAprendiz.esAprendiz(contrato);

        List<LineaLiquidacion> lineas = new ArrayList<>();

        // En modo SIMPLIFICADO no hay seguridad social ni provisiones: solo el
        // devengado y las deducciones manuales. Es una decisión de producto
        // deliberada para PyME, no una limitación.
        List<ConceptoNominaEntity> conceptos = modoCompleto
                ? conceptoService.vigentesPara(configEmpresaId(config), fechaLiquidacion)
                : List.of();

        // El salario y el auxilio siempre se emiten, aun en SIMPLIFICADO.
        lineas.addAll(lineasBase(config, bases, fechaLiquidacion, modoCompleto));

        // R3.2d — lo que pagan las novedades (horas extra, incapacidad, bono…) y los
        // conceptos propios. Ya están en las bases; aquí se vuelven líneas para que
        // lleguen al devengado y al neto.
        lineas.addAll(lineasDevengadosNovedades(nomina, config, fechaLiquidacion));
        lineas.addAll(propias);

        for (ConceptoNominaEntity c : conceptos) {
            if (esConceptoBase(c.getCodigo())) continue;   // ya emitido arriba
            if (Base.NOVEDAD.equals(c.getBase())) continue;  // lo emiten las novedades y los conceptos propios
            if (ReglasAprendiz.conceptoNoAplica(contrato, c.getCodigo())) continue;
            // En lectiva la empresa paga toda la salud: 12,5 % sobre un SMMLV.
            if (ReglasAprendiz.enLectiva(contrato) && "APO_SALUD".equals(c.getCodigo())) {
                LineaLiquidacion salud = saludLectivaAprendiz(c, nomina, config);
                if (salud != null) lineas.add(salud);
                continue;
            }
            // B-10 — el aprendiz NO va por la exoneración Ley 1607: su salud la paga
            // el patrocinador (régimen propio), así que APO_SALUD siempre se liquida.
            if (!esAprendiz && aplicaExoneracion(c, config, bases)) {
                log.debug("Concepto {} exonerado por Ley 1607", c.getCodigo());
                continue;
            }
            LineaLiquidacion linea = calcularConcepto(c, bases, contrato, config, lineas);
            if (linea != null) lineas.add(linea);
        }

        // Fondo de solidaridad: no es un concepto del catálogo porque su tarifa
        // depende de un rango, no de un porcentaje fijo.
        if (modoCompleto && !esAprendiz && Boolean.TRUE.equals(config.getAplicaFondoSolidaridad())) {
            LineaLiquidacion fsp = calcularFondoSolidaridad(bases, config, fechaLiquidacion);
            if (fsp != null) lineas.add(fsp);
        }

        // Retefuente (Fase 4.5). VA AL FINAL de las deducciones de ley: su base
        // se depura restando los aportes obligatorios, que hay que haber
        // calculado antes. El aprendiz no está sujeto (su apoyo no es salario).
        if (modoCompleto && !esAprendiz) {
            LineaLiquidacion rf = calcularRetefuente(bases, contrato, config, fechaLiquidacion, lineas);
            if (rf != null) lineas.add(rf);
        }

        // Deducciones manuales (préstamos, embargos): vienen de novedades.
        lineas.addAll(lineasDeduccionesNovedades(nomina, config, fechaLiquidacion));

        // Días como cantidad del salario, el auxilio, la salud y la pensión.
        DiasLiquidacion.asignar(lineas, nomina);

        // Distribución por proyecto/frente (Fase 4.a). Va AL FINAL: reparte las
        // líneas ya calculadas. Para una empresa sin proyectos, la cascada cae
        // al paso 3 y esto devuelve las mismas líneas sin tocar — el resultado
        // es idéntico a no tener la fase.
        lineas = distribuir(lineas, contrato, nomina, config);

        return new ResultadoLiquidacion(bases, lineas);
    }

    // ── Distribución por proyecto / frente (Fase 4.a) ────────────────────────

    /**
     * Reparte cada línea entre las dimensiones que correspondan.
     *
     * <p>Una fila por (concepto × dimensión). Si un empleado trabajó 10 días en
     * el frente A y 10 en el B, cada concepto genera dos filas al 50%.
     *
     * <p><b>La suma de las porciones cuadra exactamente con el valor original.</b>
     * La última porción recibe el remanente en vez de su porcentaje calculado:
     * repartir 100.000 entre 3 daría 33.333 × 3 = 99.999 y faltaría un peso.
     */
    private List<LineaLiquidacion> distribuir(List<LineaLiquidacion> lineas,
                                              ContratoLaboralEntity contrato,
                                              NominaEntity nomina,
                                              NominaConfigEntity config) {
        if (nomina.getPeriodo() == null || contrato.getEmpresa() == null) return lineas;

        List<DistribuidorCostoLaboral.Porcion> porciones = distribuidor.resolver(
                contrato, contrato.getEmpresa().getId(),
                nomina.getPeriodo().getFechaInicio(), nomina.getPeriodo().getFechaFin());

        // Caso mayoritario: una sola porción sin dimensión. No se toca nada.
        if (porciones.size() == 1 && !porciones.get(0).tieneDimension()) return lineas;

        List<LineaLiquidacion> out = new ArrayList<>();
        for (LineaLiquidacion l : lineas) {
            BigDecimal repartido = BigDecimal.ZERO;
            for (int i = 0; i < porciones.size(); i++) {
                var p = porciones.get(i);
                boolean ultima = (i == porciones.size() - 1);
                BigDecimal valorPorcion = distribuidor.aplicar(l.getValor(), p, repartido, ultima);
                repartido = repartido.add(valorPorcion);

                if (valorPorcion.signum() == 0 && !ultima) continue;

                LineaLiquidacion nueva = new LineaLiquidacion(
                        l.getConcepto(), l.getBase(), l.getPorcentaje(), valorPorcion, l.getTraza());
                nueva.setCantidad(l.getCantidad());
                nueva.setNovedad(l.getNovedad());
                nueva.setProyectoId(p.proyectoId());
                nueva.setFrenteId(p.frenteId());
                nueva.setCentroCostoId(p.centroCostoId());
                nueva.setPorcentajeDistrib(p.porcentaje());
                out.add(nueva);
            }
        }
        return out;
    }

    // ── Retefuente ──────────────────────────────────────────────────────────

    /**
     * Retención en la fuente.
     *
     * <p>Los "aportes obligatorios" que depuran la base son salud + pensión +
     * fondo de solidaridad <b>del empleado</b> — no los del empleador. Por eso
     * se leen de las líneas ya calculadas: la calculadora no debería recalcularlos.
     */
    private LineaLiquidacion calcularRetefuente(BasesLiquidacion bases,
                                                ContratoLaboralEntity contrato,
                                                NominaConfigEntity config,
                                                LocalDate fecha,
                                                List<LineaLiquidacion> yaCalculadas) {
        BigDecimal aportesObligatorios = valorDe(yaCalculadas, "DED_SALUD")
                .add(valorDe(yaCalculadas, "DED_PENSION"))
                .add(valorDe(yaCalculadas, "DED_FONDO_SOLIDARIDAD"));

        // Mensual: la tabla del art. 383 es del mes. Se depura con el ingreso y los
        // aportes de todo el mes y se resta lo ya retenido en las quincenas previas.
        AcumuladoMes previo = bases.getAcumuladoMes();
        CalculadoraRetefuente.Resultado r = calculadoraRetefuente.calcular(
                contrato, bases.getTotalDevengado().add(previo.devengado()),
                aportesObligatorios.add(previo.aportes()), fecha);

        if (!r.hayRetencion()) return null;
        BigDecimal valor = ReglasMensuales.pendiente(r.valor(), previo.retencion());
        if (valor.signum() == 0) return null;
        Traza traza = r.traza();
        if (previo.hayPrevias())
            traza.paso("Retención del mes", r.valor()).paso("Menos lo retenido en el mes", previo.retencion())
                    .paso("Valor", valor);

        String codigo = "2".equals(r.procedimiento()) ? "RETEFUENTE_P2" : "RETEFUENTE_P1";
        ConceptoNominaEntity concepto = buscarOpcional(config, codigo, fecha);
        if (concepto == null) {
            log.warn("Hay retención de {} pero no existe el concepto {} en el catálogo. "
                    + "NO SE ESTÁ RETENIENDO.", r.valor(), codigo);
            return null;
        }

        return new LineaLiquidacion(concepto, bases.getTotalDevengado().add(previo.devengado()), null, valor, traza);
    }

    /**
     * Lo ya liquidado al contrato en el mes (nóminas confirmadas previas). Sin
     * período o sin contrato no hay con qué cruzar: VACIO.
     */
    private AcumuladoMes acumuladoDelMes(NominaEntity nomina, ContratoLaboralEntity contrato) {
        if (nomina.getPeriodo() == null || nomina.getPeriodo().getFechaFin() == null || contrato == null
                || contrato.getId() == null)
            return AcumuladoMes.VACIO;
        return acumuladoRepo.previoDelMes(nomina.getId(), contrato.getId(), nomina.getDocumentoId(),
                nomina.getPeriodo().getFechaFin());
    }

    // ── Bases ───────────────────────────────────────────────────────────────

    /**
     * @param propioIbc   devengado de conceptos propios que constituye IBC
     * @param propioNoIbc devengado de conceptos propios que no constituye IBC
     * @param acumulado   lo ya liquidado en el mes (quincenas previas)
     */
    private BasesLiquidacion calcularBases(NominaEntity nomina,
                                           ContratoLaboralEntity contrato,
                                           NominaConfigEntity config,
                                           BigDecimal propioIbc,
                                           BigDecimal propioNoIbc,
                                           AcumuladoMes acumulado) {
        // F0 — conciliación de días. Las novedades de ausencia (incapacidad,
        // licencia no remunerada, vacaciones) restan días al salario ordinario;
        // su pago propio va como línea aparte, no aquí.
        int dias = DiasLiquidacion.diasSalario(nomina);

        // B-05 — el IBC no baja por incapacidad/maternidad/vacaciones: la cotización
        // a pensión y salud se mantiene sobre el salario pleno. Solo la licencia no
        // remunerada y la suspensión reducen la base de cotización.
        int diasIbc = DiasLiquidacion.diasIbc(nomina);

        BigDecimal salario = salarioMensual(contrato, config);

        BigDecimal salarioProporcional = salario
                .multiply(BigDecimal.valueOf(dias))
                .divide(TREINTA, ESCALA, RoundingMode.HALF_UP);

        BigDecimal salarioParaIbc = salario
                .multiply(BigDecimal.valueOf(diasIbc))
                .divide(TREINTA, ESCALA, RoundingMode.HALF_UP);

        // El auxilio de transporte tampoco se paga en los días de ausencia
        // (no hay desplazamiento durante incapacidad/vacaciones/licencia).
        BigDecimal auxilio = calcularAuxilioTransporte(salario, dias, config, contrato);

        // Separar novedades por si constituyen IBC. Esta es la corrección B2:
        // antes todas las no-deducción entraban a la base por igual.
        BigDecimal novIbc = nz(propioIbc);
        BigDecimal novNoIbc = nz(propioNoIbc);
        BigDecimal novDed = BigDecimal.ZERO;

        for (NominaNovedadEntity nov : nomina.getNovedades()) {
            BigDecimal v = nz(nov.getValorTotal());
            if (Boolean.TRUE.equals(nov.getEsDeduccion())) {
                novDed = novDed.add(v);
            } else if (Boolean.TRUE.equals(nov.getConstituyeIbc())) {
                novIbc = novIbc.add(v);
            } else {
                novNoIbc = novNoIbc.add(v);
            }
        }

        return BasesLiquidacion.builder()
                .salarioProporcional(salarioProporcional)
                .salarioParaIbc(salarioParaIbc)
                .auxilioTransporte(auxilio)
                .novedadesIbc(novIbc)
                .novedadesNoIbc(novNoIbc)
                .novedadesDeducciones(novDed)
                .smmlv(config.getSmmlv())
                .topeIbcSmmlv(config.getTopeIbcSmmlv())
                .factorIntegral(config.getFactorSalarioIntegral())
                .salarioIntegral(Boolean.TRUE.equals(contrato.getEsSalarioIntegral()))
                .acumuladoMes(acumulado)
                .build();
    }

    /**
     * Auxilio de transporte: solo si el salario es ≤ 2 SMMLV.
     *
     * <p>El salario integral NO tiene derecho: su factor prestacional ya lo cubre.
     */
    private BigDecimal calcularAuxilioTransporte(BigDecimal salario, int dias,
                                                 NominaConfigEntity config,
                                                 ContratoLaboralEntity contrato) {
        if (!"COMPLETO".equals(config.getModoNomina())) return BigDecimal.ZERO;
        if (Boolean.TRUE.equals(contrato.getEsSalarioIntegral())) return BigDecimal.ZERO;
        // B-10 — el aprendiz no recibe auxilio de transporte (su apoyo no es salario).
        if ("APRENDIZAJE".equals(contrato.getTipoContrato())) return BigDecimal.ZERO;

        BigDecimal dosSmmlv = config.getSmmlv().multiply(DOS);
        if (salario.compareTo(dosSmmlv) > 0) return BigDecimal.ZERO;

        return config.getAuxilioTransporte()
                .multiply(BigDecimal.valueOf(dias))
                .divide(TREINTA, ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Salario mensual con el que se liquida el contrato. Para el aprendiz SENA es el
     * apoyo de sostenimiento: 75 % SMMLV en lectiva y 100 % en práctica (Ley
     * 2466/2025, parametrizable). El valor del contrato se respeta si es mayor; si es
     * menor (p. ej. quedó con el % de la ley anterior) manda el mínimo de la fase.
     */
    public BigDecimal salarioMensual(ContratoLaboralEntity contrato, NominaConfigEntity config) {
        BigDecimal salario = contrato.getSalarioBase();
        if (ReglasAprendiz.esAprendiz(contrato)) {
            BigDecimal minimo = apoyoAprendiz(config, contrato.getFase());
            if (salario == null || salario.compareTo(minimo) < 0) salario = minimo;
        }
        return salario;
    }

    /**
     * B-10 — apoyo de sostenimiento del aprendiz = SMMLV × % de la fase.
     * Lectiva por defecto (la fase sin definir se trata como la más conservadora).
     */
    private BigDecimal apoyoAprendiz(NominaConfigEntity config, String fase) {
        BigDecimal pct = "PRACTICA".equals(fase)
                ? nz(config.getAprendizPctPractica())
                : nz(config.getAprendizPctLectiva());
        return config.getSmmlv().multiply(pct).divide(CIEN, ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Salud del aprendiz en etapa lectiva: la paga toda la empresa, al 12,5 % sobre
     * un SMMLV proporcional a los días cotizados (no sobre el apoyo).
     */
    private LineaLiquidacion saludLectivaAprendiz(ConceptoNominaEntity c, NominaEntity nomina,
                                                  NominaConfigEntity config) {
        int diasIbc = DiasLiquidacion.diasIbc(nomina);
        BigDecimal base = config.getSmmlv()
                .multiply(BigDecimal.valueOf(diasIbc))
                .divide(TREINTA, ESCALA, RoundingMode.HALF_UP);
        BigDecimal pct = new BigDecimal(ReglasAprendiz.PCT_SALUD_LECTIVA);
        BigDecimal valor = porcentaje(base, pct);
        if (valor.signum() == 0) return null;
        return new LineaLiquidacion(c, base, pct, valor,
                Traza.calculo("SMMLV proporcional (aprendiz lectiva)", base, pct, valor));
    }

    // ── Conceptos ───────────────────────────────────────────────────────────

    private boolean esConceptoBase(String codigo) {
        return "SALARIO".equals(codigo) || "AUX_TRANSP".equals(codigo);
    }

    private List<LineaLiquidacion> lineasBase(NominaConfigEntity config,
                                              BasesLiquidacion bases,
                                              LocalDate fecha,
                                              boolean modoCompleto) {
        List<LineaLiquidacion> out = new ArrayList<>();

        ConceptoNominaEntity salario = buscarOpcional(config, "SALARIO", fecha);
        if (salario != null) {
            out.add(LineaLiquidacion.devengado(salario, bases.getSalarioProporcional(),
                    Traza.de("Salario base", bases.getSalarioProporcional())));
        }

        if (modoCompleto && bases.getAuxilioTransporte().signum() > 0) {
            ConceptoNominaEntity aux = buscarOpcional(config, "AUX_TRANSP", fecha);
            if (aux != null) {
                out.add(LineaLiquidacion.devengado(aux, bases.getAuxilioTransporte(),
                        Traza.de("Auxilio de transporte (no constituye IBC)",
                                 bases.getAuxilioTransporte())));
            }
        }
        return out;
    }

    private LineaLiquidacion calcularConcepto(ConceptoNominaEntity c,
                                              BasesLiquidacion bases,
                                              ContratoLaboralEntity contrato,
                                              NominaConfigEntity config,
                                              List<LineaLiquidacion> yaCalculadas) {
        BigDecimal base = resolverBase(c, bases);

        // MANUAL: el motor resuelve caso por caso. Son los que no encajan en un
        // porcentaje sobre una base fija.
        if (Base.MANUAL.equals(c.getBase())) {
            return calcularManual(c, bases, contrato, yaCalculadas);
        }

        if (Base.FIJO.equals(c.getBase())) {
            BigDecimal v = nz(c.getValorFijo());
            return v.signum() == 0 ? null
                    : new LineaLiquidacion(c, BigDecimal.ZERO, null, v, Traza.de("Valor fijo", v));
        }

        if (base.signum() == 0 || c.getPorcentaje() == null) return null;

        BigDecimal valor = porcentaje(base, c.getPorcentaje());
        if (valor.signum() == 0) return null;

        Traza t = Traza.calculo(nombreBase(c.getBase()), base, c.getPorcentaje(), valor);
        return new LineaLiquidacion(c, base, c.getPorcentaje(), valor, t);
    }

    private BigDecimal resolverBase(ConceptoNominaEntity c, BasesLiquidacion b) {
        return switch (c.getBase()) {
            case Base.SALARIO             -> b.getSalarioProporcional();
            case Base.SALARIO_MAS_AUXILIO -> b.getBasePrestacional();
            case Base.IBC                 -> b.getBaseIbc();
            case Base.DEVENGADO_TOTAL     -> b.getTotalDevengado();
            default                       -> BigDecimal.ZERO;
        };
    }

    private String nombreBase(String base) {
        return switch (base) {
            case Base.SALARIO             -> "Salario proporcional";
            case Base.SALARIO_MAS_AUXILIO -> "Salario + auxilio (base prestacional)";
            case Base.IBC                 -> "IBC (sin auxilio de transporte)";
            case Base.DEVENGADO_TOTAL     -> "Total devengado";
            default                       -> base;
        };
    }

    /**
     * Conceptos con base MANUAL: los que no son "% sobre una base".
     *
     * <ul>
     *   <li><b>APO_ARL</b>: la tarifa depende del nivel de riesgo del contrato.</li>
     *   <li><b>PRO_INT_CES</b>: 12% anual sobre la provisión de cesantías, o sea
     *       sobre el resultado de otro concepto.</li>
     * </ul>
     */
    private LineaLiquidacion calcularManual(ConceptoNominaEntity c,
                                            BasesLiquidacion bases,
                                            ContratoLaboralEntity contrato,
                                            List<LineaLiquidacion> yaCalculadas) {
        switch (c.getCodigo()) {
            case "APO_ARL" -> {
                BigDecimal tarifa = tarifaArl(contrato);
                if (tarifa.signum() == 0) return null;
                BigDecimal valor = porcentaje(bases.getBaseIbc(), tarifa);
                return new LineaLiquidacion(c, bases.getBaseIbc(), tarifa, valor,
                        Traza.calculo("IBC × tarifa ARL (nivel "
                                + (contrato.getEsSalarioIntegral() != null ? "" : "") + ")",
                                bases.getBaseIbc(), tarifa, valor));
            }
            case "PRO_INT_CES" -> {
                // Depende de PRO_CESANTIAS: por eso `orden` lo pone después.
                BigDecimal cesantias = valorDe(yaCalculadas, "PRO_CESANTIAS");
                if (cesantias.signum() == 0) return null;
                // 12% anual → la provisión mensual es 12% / 12 = 1% de las cesantías del mes.
                BigDecimal valor = cesantias.multiply(nz(c.getPorcentaje()))
                        .divide(CIEN, 6, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(12), ESCALA, RoundingMode.HALF_UP);
                return new LineaLiquidacion(c, cesantias, c.getPorcentaje(), valor,
                        Traza.calculo("Provisión cesantías × 12% ÷ 12 meses",
                                cesantias, c.getPorcentaje(), valor));
            }
            default -> {
                // Retefuente (Fase 4.5), embargos (Fase 9): aún no implementados.
                // Devolver null es correcto: no emitir línea es mejor que emitir cero.
                return null;
            }
        }
    }

    private BigDecimal tarifaArl(ContratoLaboralEntity contrato) {
        // V110 agrega tarifa_arl al contrato. Mientras tanto se lee del empleado.
        if (contrato.getEmpleado() != null && contrato.getEmpleado().getArl() != null) {
            return nz(contrato.getEmpleado().getArl().getPorcentaje());
        }
        return BigDecimal.ZERO;
    }

    // ── Exoneración Ley 1607 ────────────────────────────────────────────────

    /**
     * ¿Este concepto está exonerado?
     *
     * <p><b>Se evalúa POR EMPLEADO</b> (su devengado), no por empresa: un mismo
     * cliente puede tener empleados exonerados y no exonerados.
     *
     * <p>Aplica solo a salud empleador, SENA e ICBF. <b>No</b> a CCF ni a pensión.
     */
    private boolean aplicaExoneracion(ConceptoNominaEntity c,
                                      NominaConfigEntity config,
                                      BasesLiquidacion bases) {
        if (!Boolean.TRUE.equals(config.getAplicaExoneracion1607())) return false;
        if (!EXONERABLES_1607.contains(c.getCodigo())) return false;

        BigDecimal umbral = config.getSmmlv().multiply(config.getUmbralExoneracionSmmlv());
        // B-05(a) — se compara contra la base SALARIAL (IBC sin tope), no el total
        // devengado: el auxilio de transporte y lo no salarial no cuentan para el
        // umbral de 10 SMMLV de la exoneración.
        // Pendiente contador: el borde exacto (< vs <=) y la mensualización cuando
        // el mes es parcial (hoy compara el IBC proporcional contra 10 SMMLV plenos).
        // Quincenas: el umbral es mensual, se suma el IBC ya cotizado en el mes.
        return bases.getBaseIbcSinTope().add(bases.getAcumuladoMes().ibc()).compareTo(umbral) < 0;
    }

    // ── Fondo de solidaridad pensional ──────────────────────────────────────

    private LineaLiquidacion calcularFondoSolidaridad(BasesLiquidacion bases,
                                                      NominaConfigEntity config,
                                                      LocalDate fecha) {
        BigDecimal ibcEnSmmlv = bases.ibcEnSmmlv(config.getSmmlv());

        FondoSolidaridadRangoEntity rango =
                fondoRepo.findRango(fecha.getYear(), ibcEnSmmlv).orElse(null);
        if (rango == null) return null;   // no alcanza los 4 SMMLV: no aplica

        BigDecimal tarifa = rango.pctTotal();
        if (tarifa.signum() == 0) return null;

        // Mensual: tarifa sobre el IBC del mes menos lo ya descontado en las quincenas
        // previas. En una nómina mensual el acumulado es cero y queda igual que antes.
        AcumuladoMes previo = bases.getAcumuladoMes();
        BigDecimal valor = ReglasMensuales.pendiente(porcentaje(bases.ibcMes(), tarifa), previo.fsp());
        if (valor.signum() == 0) return null;
        ConceptoNominaEntity concepto = buscarOpcional(config, "DED_FONDO_SOLIDARIDAD", fecha);
        if (concepto == null) {
            log.warn("Fondo de solidaridad aplica (IBC = {} SMMLV) pero no existe el concepto "
                    + "DED_FONDO_SOLIDARIDAD en el catálogo. No se descuenta.", ibcEnSmmlv);
            return null;
        }

        String smmlv = ibcEnSmmlv.setScale(2, RoundingMode.HALF_UP).toPlainString();
        Traza traza = Traza.calculo(previo.hayPrevias()
                ? "IBC del mes × tarifa fondo solidaridad (" + smmlv + " SMMLV)"
                : "IBC × tarifa fondo solidaridad (" + smmlv + " SMMLV)", bases.ibcMes(), tarifa,
                porcentaje(bases.ibcMes(), tarifa));
        if (previo.hayPrevias()) traza.paso("Menos lo descontado en el mes", previo.fsp()).paso("Valor", valor);
        return new LineaLiquidacion(concepto, bases.ibcMes(), tarifa, valor, traza);
    }

    // ── Novedades y conceptos propios (R3.2d) ───────────────────────────────

    /** 400 con la causa si las novedades incumplen las permitidas de la vinculación. */
    private void validarNovedadesPermitidas(NominaEntity nomina, ContratoLaboralEntity contrato) {
        if (nomina.getNovedades() == null || nomina.getNovedades().isEmpty()) return;
        List<ReglasNovedadesPermitidas.Novedad> novedades = nomina.getNovedades().stream()
                .map(n -> new ReglasNovedadesPermitidas.Novedad(n.getTipo(), n.getCantidad(), n.getValorTotal(),
                        Boolean.TRUE.equals(n.getEsDeduccion())))
                .toList();
        Optional<String> error = ReglasNovedadesPermitidas.validar(
                reglasVinculacion.novedadesPermitidas(contrato.getId()), novedades);
        if (error.isPresent()) throw new GlobalException(HttpStatus.BAD_REQUEST, error.get());
    }

    /**
     * Una línea de devengado por cada novedad que paga, con el concepto de su tipo
     * (V217). Si el tipo no tiene concepto, cae en OTRO_DEVENGO.
     */
    private List<LineaLiquidacion> lineasDevengadosNovedades(NominaEntity nomina,
                                                             NominaConfigEntity config,
                                                             LocalDate fecha) {
        List<LineaLiquidacion> out = new ArrayList<>();
        if (nomina.getNovedades() == null) return out;
        for (NominaNovedadEntity nov : nomina.getNovedades()) {
            if (Boolean.TRUE.equals(nov.getEsDeduccion())) continue;
            BigDecimal v = nz(nov.getValorTotal());
            if (v.signum() == 0) continue;
            ConceptoNominaEntity c = buscarOpcional(config, nov.getTipo(), fecha);
            if (c == null) c = buscarOpcional(config, "OTRO_DEVENGO", fecha);
            if (c == null) continue;
            Traza t = new Traza()
                    .paso(nov.getTipo() + (nov.getDescripcion() != null ? " — " + nov.getDescripcion() : ""), "")
                    .paso("Cantidad", nz(nov.getCantidad()))
                    .paso("Valor unitario", nz(nov.getValorUnitario()))
                    .paso("Total", v);
            LineaLiquidacion l = LineaLiquidacion.devengado(c, v, t);
            l.setCantidad(nov.getCantidad());
            l.setNovedad(nov);
            out.add(l);
        }
        return out;
    }

    /**
     * Conceptos propios de la vinculación vigentes en el período. Valor: porcentaje
     * del salario proporcional, cantidad × valor, o el valor tal cual. Solo
     * devengados y deducciones; un aporte o provisión propio no aplica.
     */
    private List<LineaLiquidacion> lineasConceptosPropios(NominaEntity nomina, ContratoLaboralEntity contrato,
                                                          NominaConfigEntity config, LocalDate fecha,
                                                          BigDecimal salarioProporcional) {
        List<LineaLiquidacion> out = new ArrayList<>();
        if (contrato.getId() == null || nomina.getPeriodo() == null) return out;
        // Sin días de salario (retroactivo, definitiva con el salario ya pagado) los
        // conceptos propios ya se pagaron en la nómina del período: no se repiten.
        if (nomina.getDiasTrabajados() != null && nomina.getDiasTrabajados() == 0) return out;
        for (ConceptoPropio p : reglasVinculacion.conceptosVigentes(contrato.getId(),
                nomina.getPeriodo().getFechaInicio(), nomina.getPeriodo().getFechaFin())) {
            ConceptoNominaEntity c = buscarOpcional(config, p.codigo(), fecha);
            if (c == null || !(Clase.DEVENGADO.equals(c.getClase()) || Clase.DEDUCCION.equals(c.getClase()))) {
                log.warn("Concepto propio '{}' del contrato {} no aplica (no existe o no es devengado/deducción)",
                        p.codigo(), contrato.getId());
                continue;
            }
            LineaLiquidacion l = lineaPropia(c, p, salarioProporcional);
            if (l != null) out.add(l);
        }
        return out;
    }

    /** Línea de un concepto propio, o null si no tiene valor. */
    private static LineaLiquidacion lineaPropia(ConceptoNominaEntity c, ConceptoPropio p,
                                                BigDecimal salarioProporcional) {
        String origen = "Concepto propio de la vinculación" + (p.observacion() != null ? " — " + p.observacion() : "");
        if (p.porcentaje() != null && p.porcentaje().signum() != 0) {
            BigDecimal valor = porcentaje(salarioProporcional, p.porcentaje());
            if (valor.signum() == 0) return null;
            Traza t = new Traza().paso(origen, "")
                    .paso("Salario proporcional", salarioProporcional)
                    .paso("Porcentaje", p.porcentaje() + " %")
                    .paso("Resultado", valor);
            return new LineaLiquidacion(c, salarioProporcional, p.porcentaje(), valor, t);
        }
        BigDecimal unitario = nz(p.valor());
        BigDecimal valor = p.cantidad() != null && p.cantidad().signum() != 0
                ? p.cantidad().multiply(unitario).setScale(ESCALA, RoundingMode.HALF_UP)
                : unitario;
        if (valor.signum() == 0) return null;
        Traza t = new Traza().paso(origen, "");
        if (p.cantidad() != null && p.cantidad().signum() != 0)
            t.paso("Cantidad", p.cantidad()).paso("Valor unitario", unitario);
        t.paso("Total", valor);
        LineaLiquidacion l = new LineaLiquidacion(c, BigDecimal.ZERO, null, valor, t);
        l.setCantidad(p.cantidad());
        return l;
    }

    /** Devengado de las líneas propias que constituye (o no) IBC. */
    private static BigDecimal devengadoPropio(List<LineaLiquidacion> propias, boolean constituyeIbc) {
        return propias.stream()
                .filter(l -> Clase.DEVENGADO.equals(l.getConcepto().getClase()))
                .filter(l -> constituyeIbc == Boolean.TRUE.equals(l.getConcepto().getConstituyeIbc()))
                .map(LineaLiquidacion::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Deducciones de novedades ────────────────────────────────────────────

    private List<LineaLiquidacion> lineasDeduccionesNovedades(NominaEntity nomina,
                                                              NominaConfigEntity config,
                                                              LocalDate fecha) {
        List<LineaLiquidacion> out = new ArrayList<>();
        ConceptoNominaEntity otros = buscarOpcional(config, "DED_OTROS", fecha);
        if (otros == null) return out;

        for (NominaNovedadEntity nov : nomina.getNovedades()) {
            if (!Boolean.TRUE.equals(nov.getEsDeduccion())) continue;
            BigDecimal v = nz(nov.getValorTotal());
            if (v.signum() == 0) continue;
            LineaLiquidacion l = new LineaLiquidacion(otros, BigDecimal.ZERO, null, v,
                    Traza.de(nov.getTipo() + (nov.getDescripcion() != null ? " — " + nov.getDescripcion() : ""), v));
            l.setNovedad(nov);
            out.add(l);
        }
        return out;
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private ConceptoNominaEntity buscarOpcional(NominaConfigEntity config, String codigo, LocalDate fecha) {
        try {
            return conceptoService.porCodigo(configEmpresaId(config), codigo, fecha);
        } catch (RuntimeException e) {
            log.warn("Concepto '{}' no encontrado en {}: {}", codigo, fecha, e.getMessage());
            return null;
        }
    }

    private Integer configEmpresaId(NominaConfigEntity config) {
        return config.getEmpresa() != null ? config.getEmpresa().getId() : null;
    }

    private BigDecimal valorDe(List<LineaLiquidacion> lineas, String codigo) {
        return lineas.stream()
                .filter(l -> codigo.equals(l.getConcepto().getCodigo()))
                .map(LineaLiquidacion::getValor)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal porcentaje(BigDecimal base, BigDecimal pct) {
        return base.multiply(pct).divide(CIEN, ESCALA, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── Resultado ───────────────────────────────────────────────────────────

    /** Lo que produce el motor: las bases usadas y las líneas de detalle. */
    public record ResultadoLiquidacion(BasesLiquidacion bases, List<LineaLiquidacion> lineas) {

        public BigDecimal totalPorClase(String clase) {
            return lineas.stream()
                    .filter(l -> clase.equals(l.getConcepto().getClase()))
                    .map(LineaLiquidacion::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal valorDeConcepto(String codigo) {
            return lineas.stream()
                    .filter(l -> codigo.equals(l.getConcepto().getCodigo()))
                    .map(LineaLiquidacion::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal totalDevengado()   { return totalPorClase(Clase.DEVENGADO); }
        public BigDecimal totalDeducciones() { return totalPorClase(Clase.DEDUCCION); }
        public BigDecimal totalAportes()     { return totalPorClase(Clase.APORTE_EMPLEADOR); }
        public BigDecimal totalProvisiones() { return totalPorClase(Clase.PROVISION); }

        public BigDecimal netoPagar() {
            return totalDevengado().subtract(totalDeducciones()).max(BigDecimal.ZERO);
        }
    }
}
