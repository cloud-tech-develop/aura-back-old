package com.cloud_technological.aura_pos.services.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.DiferidoService;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.DeterioroSugerido;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.ActaCierre;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.Chequeo;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.ChequeoCierre;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.Comparativo;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.LineaComparativo;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.ResultadoAjuste;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CierreMesQueryRepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableQueryRepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.cartera.CastigoCarteraService;
import com.cloud_technological.aura_pos.services.implementations.ActivoFijoServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;

/**
 * Cierre de mes, fases 2 a 5 (docs/PLAN_CIERRE_CONTABLE.md): lista de chequeo
 * con semáforos, ajustes asistidos, acta y comparativos.
 *
 * <p>Los requisitos que ya bloqueaban el cierre (borradores, extractos sin
 * conciliar, descuadres) siguen bloqueándolo en {@code PeriodoContableServiceImpl};
 * aquí se muestran antes de intentarlo. El resto son advertencias: el contador
 * decide, pero queda escrito en el acta que cerró con ellas.
 */
@Service
@RequiredArgsConstructor
public class CierreMesService {

    private static final BigDecimal TOLERANCIA = BigDecimal.ONE;
    private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre" };

    private static final String G_REQUISITOS = "Requisitos para cerrar";
    private static final String G_AJUSTES = "Ajustes del mes";
    private static final String G_CALIDAD = "Calidad de la información";
    private static final String G_CRUCES = "Cruces del auxiliar contra el mayor (a hoy)";

    private final CierreMesQueryRepository query;
    private final PeriodoContableJPARepository periodoRepo;
    private final PeriodoContableQueryRepository periodoQuery;
    private final ConfiguracionContableService config;
    private final ActivoFijoServiceImpl activoService;
    private final DiferidoService diferidoService;
    private final CastigoCarteraService castigoService;
    private final ObjectMapper objectMapper;

    // ── Fase 2: lista de chequeo ─────────────────────────────────────────────

    public ChequeoCierre chequear(Integer empresaId, Long periodoId) {
        PeriodoContableEntity periodo = periodo(empresaId, periodoId);
        YearMonth ym = YearMonth.of(periodo.getAnio(), periodo.getMes());
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();
        String clave = ym.toString();

        ChequeoCierre out = new ChequeoCierre();
        out.setPeriodoId(periodoId);
        out.setPeriodo(nombre(ym));
        out.setEstadoPeriodo(periodo.getEstado());
        out.setDesde(desde);
        out.setHasta(hasta);
        out.setGeneradoEn(LocalDateTime.now());
        List<Chequeo> c = out.getChequeos();

        // Requisitos (bloquean)
        List<PeriodoContableEntity> anteriores = periodoRepo
                .findByEmpresaIdAndEstadoOrderByAnioAscMesAsc(empresaId, "ABIERTO").stream()
                .filter(p -> !p.getId().equals(periodoId)
                        && YearMonth.of(p.getAnio(), p.getMes()).isBefore(ym))
                .toList();
        c.add(lista("MESES_ANTERIORES", G_REQUISITOS, "Meses anteriores cerrados", BLOQUEA(),
                anteriores.stream().map(p -> nombre(YearMonth.of(p.getAnio(), p.getMes()))).toList(),
                "Todos los meses anteriores están cerrados.",
                "Hay meses anteriores abiertos: se cierran en orden.", null, null, null));

        c.add(lista("NOTAS_BORRADOR", G_REQUISITOS, "Notas contables en borrador", BLOQUEA(),
                query.borradores(empresaId, periodoId, true),
                "No hay notas contables en borrador.",
                "Hay notas contables en borrador: contabilícelas o anúlelas.",
                "RUTA", "/contabilidad/notas", "Ir a notas contables"));

        c.add(lista("BORRADORES", G_REQUISITOS, "Comprobantes automáticos en borrador", BLOQUEA(),
                query.borradores(empresaId, periodoId, false),
                "No hay comprobantes automáticos pendientes de aprobar.",
                "Hay comprobantes en borrador (deterioro, causaciones, revisión…): apruébelos o anúlelos.",
                "RUTA", "/contabilidad/revision", "Revisar comprobantes"));

        c.add(lista("DESCUADRES", G_REQUISITOS, "Comprobantes cuadrados (débito = crédito)", BLOQUEA(),
                periodoQuery.comprobantesSinCuadre(periodoId),
                "Todos los comprobantes cuadran.",
                "Hay comprobantes descuadrados.", null, null, null));

        c.add(lista("EXTRACTOS", G_REQUISITOS, "Extractos bancarios del mes conciliados", BLOQUEA(),
                query.extractosAbiertos(empresaId, clave),
                "No hay extractos del mes sin conciliar.",
                "Hay extractos importados sin conciliar.",
                "RUTA", "/contabilidad/conciliacion", "Ir a conciliación"));

        // Ajustes del mes (advertencias con botón)
        c.add(lista("CONCILIACION", G_AJUSTES, "Bancos conciliados", CierreMesDtos.ADVERTENCIA,
                query.bancosSinConciliar(empresaId, clave, desde, hasta),
                "Todos los bancos con movimiento tienen extracto conciliado.",
                "Bancos con movimiento en el mes sin extracto conciliado.",
                "RUTA", "/contabilidad/conciliacion", "Conciliar"));

        c.add(lista("DEPRECIACION", G_AJUSTES, "Depreciación del mes", CierreMesDtos.ADVERTENCIA,
                query.activosSinDepreciar(empresaId, periodoId, hasta),
                "Los activos fijos del mes ya están depreciados (o no hay activos).",
                "Activos sin la depreciación de este mes.",
                "AJUSTE", "depreciacion", "Calcular depreciación"));

        long diferidos = query.diferidosSinAmortizar(empresaId, clave, desde, hasta);
        Chequeo dif = base("DIFERIDOS", G_AJUSTES, "Amortización de diferidos");
        dif.setEstado(diferidos == 0 ? CierreMesDtos.OK : CierreMesDtos.ADVERTENCIA);
        dif.setDetalle(diferidos == 0 ? "No hay cuotas de diferidos pendientes para el mes."
                : diferidos + " diferido(s) sin la cuota de este mes.");
        if (diferidos > 0) accion(dif, "AJUSTE", "diferidos", "Amortizar diferidos");
        c.add(dif);

        Chequeo nomina = base("NOMINA", G_AJUSTES, "Nómina causada");
        long empleados = query.empleadosActivos(empresaId, hasta);
        if (empleados == 0) {
            nomina.setEstado(CierreMesDtos.NO_APLICA);
            nomina.setDetalle("La empresa no tiene empleados activos.");
        } else if (query.asientosDeOrigen(empresaId, "NOMINA", desde, hasta) > 0) {
            nomina.setEstado(CierreMesDtos.OK);
            nomina.setDetalle("Hay nómina contabilizada en el mes.");
        } else {
            nomina.setEstado(CierreMesDtos.ADVERTENCIA);
            nomina.setDetalle(empleados + " empleado(s) activo(s) y ninguna nómina contabilizada en el mes.");
            accion(nomina, "RUTA", "/nomina/liquidacion", "Ir a nómina");
        }
        c.add(nomina);

        c.add(deterioro(empresaId));

        // Calidad de la información
        c.add(lista("SIN_TERCERO", G_CALIDAD, "Movimientos con tercero", CierreMesDtos.ADVERTENCIA,
                query.movimientosSinTercero(empresaId, desde, hasta),
                "Todas las cuentas de deudores, proveedores, retenciones y anticipos llevan tercero.",
                "Movimientos sin tercero en cuentas que lo exigen: no salen en el auxiliar ni en exógena.",
                "RUTA", "/contabilidad/libros", "Ver auxiliar"));

        c.add(lista("NATURALEZA", G_CALIDAD, "Saldos según la naturaleza de la cuenta", CierreMesDtos.ADVERTENCIA,
                query.saldosContrarios(empresaId, hasta),
                "Ninguna cuenta tiene saldo contrario a su naturaleza.",
                "Cuentas con saldo contrario (p. ej. caja o proveedores al revés).",
                "RUTA", "/contabilidad/balance-prueba", "Ver balance de prueba"));

        // Cruces auxiliar contra mayor (a hoy: el auxiliar no tiene foto por fecha)
        cruces(empresaId, c);

        out.setBloqueos((int) c.stream().filter(x -> CierreMesDtos.BLOQUEA.equals(x.getEstado())).count());
        out.setAdvertencias((int) c.stream().filter(x -> CierreMesDtos.ADVERTENCIA.equals(x.getEstado())).count());
        out.setPuedeCerrar("ABIERTO".equals(periodo.getEstado()) && out.getBloqueos() == 0
                && !ym.isAfter(YearMonth.now()));
        return out;
    }

    private Chequeo deterioro(Integer empresaId) {
        Chequeo ch = base("DETERIORO", G_AJUSTES, "Deterioro de cartera");
        try {
            DeterioroSugerido d = castigoService.deterioroSugerido(empresaId);
            if (d.getTramos().isEmpty()) {
                ch.setEstado(CierreMesDtos.NO_APLICA);
                ch.setDetalle("La política de cartera no tiene tramos de deterioro.");
            } else if (d.getBorradoresPendientes() > 0) {
                ch.setEstado(CierreMesDtos.ADVERTENCIA);
                ch.setDetalle("Hay una propuesta de deterioro en borrador sin aprobar.");
                accion(ch, "RUTA", "/contabilidad/revision", "Revisar comprobantes");
            } else if (d.getAjuste().compareTo(TOLERANCIA) > 0) {
                ch.setEstado(CierreMesDtos.ADVERTENCIA);
                ch.setDetalle("Falta provisionar según la política de cartera.");
                ch.setValorAuxiliar(d.getTotalCalculado());
                ch.setValorMayor(d.getProvisionActual());
                ch.setDiferencia(d.getAjuste());
                accion(ch, "AJUSTE", "deterioro", "Proponer deterioro");
            } else {
                ch.setEstado(CierreMesDtos.OK);
                ch.setDetalle("La provisión de cartera cubre el deterioro calculado.");
            }
        } catch (RuntimeException e) {
            ch.setEstado(CierreMesDtos.NO_APLICA);
            ch.setDetalle("No se pudo calcular: " + e.getMessage());
        }
        return ch;
    }

    private void cruces(Integer empresaId, List<Chequeo> c) {
        c.add(cruce("CRUCE_CARTERA", "Cartera contra clientes",
                query.carteraAuxiliar(empresaId),
                saldoConcepto(empresaId, ConceptoContable.CLIENTES, null), false,
                "/cuentas/cuentas-por-cobrar"));
        c.add(cruce("CRUCE_PROVEEDORES", "Cuentas por pagar contra proveedores",
                query.proveedoresAuxiliar(empresaId),
                saldoConcepto(empresaId, ConceptoContable.PROVEEDORES, null), true,
                "/cuentas/cuentas-por-pagar"));
        c.add(cruce("CRUCE_INVENTARIO", "Inventario valorizado contra 14",
                query.inventarioAuxiliar(empresaId),
                query.saldoPrefijo(empresaId, "14", "1499", null), false, "/inventario/stock"));

        Map<String, Object> af = query.activosAuxiliar(empresaId);
        if (((Number) af.get("fichas")).longValue() > 0) {
            c.add(cruce("CRUCE_ACTIVOS", "Fichas de activos contra cuentas de activo",
                    (BigDecimal) af.get("costo"),
                    query.saldoCuentas(empresaId, query.cuentasActivo(empresaId, "cuenta_activo_id")), false,
                    "/contabilidad/activos-fijos"));
            c.add(cruce("CRUCE_DEPRECIACION", "Depreciación acumulada de las fichas contra el mayor",
                    (BigDecimal) af.get("depreciacion"),
                    query.saldoCuentas(empresaId, query.cuentasActivo(empresaId, "cuenta_depreciacion_id")), true,
                    "/contabilidad/activos-fijos"));
        }

        for (Map<String, Object> b : query.bancos(empresaId)) {
            Long cuentaId = ((Number) b.get("cuenta_contable_id")).longValue();
            c.add(cruce("CRUCE_BANCO_" + b.get("id"), "Banco " + b.get("nombre") + " (tesorería contra mayor)",
                    (BigDecimal) b.get("saldo"), query.saldoCuentas(empresaId, List.of(cuentaId)), false,
                    "/tesoreria/cuentas-bancarias"));
        }

        BigDecimal caja = saldoConcepto(empresaId, ConceptoContable.CAJA, null);
        Chequeo ch = base("CAJA_NEGATIVA", G_CRUCES, "Caja contable sin saldo negativo");
        ch.setValorMayor(caja);
        if (caja.compareTo(TOLERANCIA.negate()) < 0) {
            ch.setEstado(CierreMesDtos.ADVERTENCIA);
            ch.setDetalle("La caja quedó en negativo en el mayor: salió más plata de la que entró. "
                    + "Suele ser un pago registrado por caja que salió del banco.");
            accion(ch, "RUTA", "/contabilidad/libros", "Ver auxiliar de caja");
        } else {
            ch.setEstado(CierreMesDtos.OK);
            ch.setDetalle("La caja tiene saldo positivo.");
        }
        c.add(ch);
    }

    /**
     * El auxiliar y el mayor deben dar lo mismo. Las cuentas crédito (proveedores,
     * depreciación) se comparan en positivo.
     */
    private Chequeo cruce(String codigo, String titulo, BigDecimal auxiliar, BigDecimal mayorDebito,
            boolean naturalezaCredito, String ruta) {
        BigDecimal mayor = naturalezaCredito ? mayorDebito.negate() : mayorDebito;
        BigDecimal dif = auxiliar.subtract(mayor);
        Chequeo ch = base(codigo, G_CRUCES, titulo);
        ch.setValorAuxiliar(auxiliar);
        ch.setValorMayor(mayor);
        ch.setDiferencia(dif);
        if (auxiliar.signum() == 0 && mayor.signum() == 0) {
            ch.setEstado(CierreMesDtos.NO_APLICA);
            ch.setDetalle("Sin saldo en el módulo ni en el mayor.");
        } else if (dif.abs().compareTo(TOLERANCIA) <= 0) {
            ch.setEstado(CierreMesDtos.OK);
            ch.setDetalle("El módulo y la contabilidad dan lo mismo.");
        } else {
            ch.setEstado(CierreMesDtos.ADVERTENCIA);
            ch.setDetalle("El módulo y la contabilidad no dan lo mismo: revise documentos sin asiento, "
                    + "asientos manuales a esta cuenta o saldos iniciales.");
            accion(ch, "RUTA", ruta, "Ver módulo");
        }
        return ch;
    }

    private BigDecimal saldoConcepto(Integer empresaId, ConceptoContable concepto, LocalDate hasta) {
        try {
            String codigo = config.resolverCuenta(empresaId, concepto).getCodigo();
            // Se toma la cuenta de mayor (4 dígitos) para incluir sus subcuentas.
            String prefijo = codigo.length() > 4 ? codigo.substring(0, 4) : codigo;
            return query.saldoPrefijo(empresaId, prefijo, null, hasta);
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }

    // ── Fase 3: ajustes asistidos ────────────────────────────────────────────

    @Transactional
    public ResultadoAjuste ajustar(Integer empresaId, Long usuarioId, Long periodoId, String tipo) {
        PeriodoContableEntity periodo = periodo(empresaId, periodoId);
        if (!"ABIERTO".equals(periodo.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El período está cerrado: reábralo para ajustar.");
        LocalDate fin = YearMonth.of(periodo.getAnio(), periodo.getMes()).atEndOfMonth();
        ResultadoAjuste r = new ResultadoAjuste();
        r.setTipo(tipo);
        switch (tipo == null ? "" : tipo) {
            case "depreciacion" -> {
                int n = activoService.calcularDepreciacionPeriodo(periodoId, null, empresaId,
                        usuarioId != null ? usuarioId.intValue() : null).size();
                r.setCantidad(n);
                r.setMensaje(n == 0 ? "No había activos por depreciar en el mes."
                        : "Depreciación contabilizada: " + n + " activo(s). Se reversa desde Activos fijos.");
            }
            case "diferidos" -> {
                int n = diferidoService.amortizar(empresaId, fin);
                r.setCantidad(n);
                r.setMensaje(n == 0 ? "No había cuotas de diferidos pendientes."
                        : n + " cuota(s) de diferidos amortizada(s).");
            }
            case "deterioro" -> {
                castigoService.proponerDeterioro(empresaId, usuarioId);
                r.setCantidad(1);
                r.setMensaje("Deterioro propuesto en borrador: apruébelo en Revisión de comprobantes.");
            }
            default -> throw new GlobalException(HttpStatus.BAD_REQUEST, "Ajuste desconocido: " + tipo);
        }
        return r;
    }

    // ── Fase 4: acta de cierre ───────────────────────────────────────────────

    /**
     * Lo llama el cierre justo antes de cerrar: el acta imprime esta foto. Va en
     * su propia transacción para que un error aquí no aborte la del cierre.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardarFoto(Integer empresaId, Long periodoId) {
        try {
            query.guardarChequeo(periodoId, objectMapper.writeValueAsString(chequear(empresaId, periodoId)));
        } catch (Exception e) {
            // El acta sin foto se arma con el chequeo del momento; no se frena el cierre por esto.
            LoggerFactory.getLogger(CierreMesService.class)
                    .warn("No se guardó el chequeo del cierre del período {}: {}", periodoId, e.getMessage());
        }
    }

    public ActaCierre acta(Integer empresaId, Long periodoId) {
        PeriodoContableEntity periodo = periodo(empresaId, periodoId);
        YearMonth ym = YearMonth.of(periodo.getAnio(), periodo.getMes());
        ActaCierre a = new ActaCierre();
        a.setPeriodoId(periodoId);
        a.setPeriodo(nombre(ym));
        a.setEstado(periodo.getEstado());
        a.setFechaCierre(periodo.getFechaCierre());
        a.setObservaciones(periodo.getObservaciones());
        a.setReaperturas(periodo.getReaperturas() != null ? periodo.getReaperturas() : 0);

        Map<String, Object> ids = query.empresaYUsuario(empresaId, periodo.getUsuarioCierreId());
        a.setEmpresaNombre((String) ids.get("empresa"));
        a.setEmpresaNit((String) ids.get("nit"));
        a.setCerradoPor((String) ids.get("usuario"));

        String guardado = "CERRADO".equals(periodo.getEstado()) ? query.chequeoGuardado(periodoId) : null;
        if (guardado != null) {
            try {
                a.setChequeo(objectMapper.readValue(guardado, ChequeoCierre.class));
                a.setChequeoGuardado(true);
            } catch (Exception e) {
                a.setChequeo(chequear(empresaId, periodoId));
            }
        } else {
            a.setChequeo(chequear(empresaId, periodoId));
        }

        a.setBalance(query.balance(empresaId, ym.atDay(1), ym.atEndOfMonth()));
        Map<String, Object> cierre = query.asientoCierre(empresaId, periodoId);
        if (cierre != null) {
            a.setAsientoCierreNumero((String) cierre.get("numero_comprobante"));
            a.setAsientoCierre(query.lineasAsiento(((Number) cierre.get("id")).longValue()));
        }

        // Resultado del mes, sin el asiento de cierre.
        LocalDate d = ym.atDay(1);
        LocalDate h = ym.atEndOfMonth();
        for (LineaComparativo l : query.comparativo(empresaId, d, h, d, h, d, h)) {
            switch (l.getSeccion()) {
                case "INGRESO" -> a.setIngresos(a.getIngresos().add(l.getActual()));
                case "COSTO" -> a.setCostos(a.getCostos().add(l.getActual()));
                default -> a.setGastos(a.getGastos().add(l.getActual()));
            }
        }
        a.setUtilidad(a.getIngresos().subtract(a.getCostos()).subtract(a.getGastos()));
        return a;
    }

    // ── Fase 5: comparativos ─────────────────────────────────────────────────

    public Comparativo comparativo(Integer empresaId, Long periodoId) {
        PeriodoContableEntity periodo = periodo(empresaId, periodoId);
        YearMonth ym = YearMonth.of(periodo.getAnio(), periodo.getMes());
        YearMonth ant = ym.minusMonths(1);
        YearMonth anio = ym.minusYears(1);
        Comparativo c = new Comparativo();
        c.setPeriodo(nombre(ym));
        c.setMesAnterior(nombre(ant));
        c.setAnioAnterior(nombre(anio));
        c.setLineas(query.comparativo(empresaId, ym.atDay(1), ym.atEndOfMonth(), ant.atDay(1),
                ant.atEndOfMonth(), anio.atDay(1), anio.atEndOfMonth()));
        return c;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private PeriodoContableEntity periodo(Integer empresaId, Long periodoId) {
        return periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));
    }

    private static String BLOQUEA() {
        return CierreMesDtos.BLOQUEA;
    }

    private static String nombre(YearMonth ym) {
        String m = MESES[ym.getMonthValue() - 1];
        return Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + ym.getYear();
    }

    private static Chequeo base(String codigo, String grupo, String titulo) {
        Chequeo ch = new Chequeo();
        ch.setCodigo(codigo);
        ch.setGrupo(grupo);
        ch.setTitulo(titulo);
        return ch;
    }

    private static void accion(Chequeo ch, String tipo, String valor, String texto) {
        ch.setAccionTipo(tipo);
        ch.setAccionValor(valor);
        ch.setAccionTexto(texto);
    }

    /** Chequeo que falla si la lista trae algo; guarda hasta 10 ejemplos. */
    private static Chequeo lista(String codigo, String grupo, String titulo, String siFalla, List<String> hallazgos,
            String textoOk, String textoFalla, String accionTipo, String accionValor, String accionTexto) {
        Chequeo ch = base(codigo, grupo, titulo);
        if (hallazgos.isEmpty()) {
            ch.setEstado(CierreMesDtos.OK);
            ch.setDetalle(textoOk);
        } else {
            ch.setEstado(siFalla);
            ch.setDetalle(textoFalla + " (" + hallazgos.size() + ")");
            ch.setEjemplos(new ArrayList<>(hallazgos.subList(0, Math.min(10, hallazgos.size()))));
            if (accionTipo != null) accion(ch, accionTipo, accionValor, accionTexto);
        }
        return ch;
    }
}
