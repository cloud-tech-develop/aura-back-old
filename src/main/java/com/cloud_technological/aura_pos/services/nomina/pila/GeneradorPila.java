package com.cloud_technological.aura_pos.services.nomina.pila;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.ContratoAfiliacionEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EntidadSeguridadSocialEntity.Tipo;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.PilaCotizanteEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.entity.PilaPlanillaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoLaboralJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaCotizanteRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEncabezadoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaPlanillaRepo;
import com.cloud_technological.aura_pos.services.implementations.AfiliacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Generador de PILA (Fase 6).
 *
 * <p><b>Consume la salida de las cinco fases anteriores:</b>
 * <ul>
 *   <li>Fase 1 → identificación desagregada (4 componentes de nombre)</li>
 *   <li>Fase 2 → historial salarial (bandera {@code vsp})</li>
 *   <li>Fase 3 → conceptos y detalle</li>
 *   <li>Fase 0 → IBC correcto, topes, fondo de solidaridad, exoneración</li>
 *   <li>Fase 5.5 → afiliaciones (a quién reportarle, y traslados)</li>
 * </ul>
 *
 * <h2>🚧 Alcance de esta implementación</h2>
 * Genera el <b>modelo</b> de la planilla: encabezado, registro 01 y registro 02
 * con banderas, IBC y días por subsistema.
 *
 * <p><b>NO genera el archivo plano.</b> Ese paso depende del operador (pendiente
 * #6: ¿Aportes en Línea, SOI? ¿API o plano?) y de su especificación de columnas.
 * Con el modelo armado, escribir el plano es mecánico; sin saber el operador,
 * sería adivinar.
 *
 * <p><b>Nada de esto se ha validado contra un operador real.</b>
 */
@Slf4j
@Service
public class GeneradorPila {

    private final PilaEncabezadoRepo encabezadoRepo;
    private final PilaPlanillaRepo planillaRepo;
    private final PilaCotizanteRepo cotizanteRepo;
    private final ContratoLaboralJPARepository contratoRepo;
    private final NominaJPARepository nominaRepo;
    private final NominaConfigJPARepository configRepo;
    private final EmpresaJPARepository empresaRepo;
    private final AfiliacionService afiliacionService;
    private final ResolverNovedadesPila resolverNovedades;
    private final com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaAportanteConfigRepo aportanteConfigRepo;

    public GeneradorPila(PilaEncabezadoRepo encabezadoRepo,
                         PilaPlanillaRepo planillaRepo,
                         PilaCotizanteRepo cotizanteRepo,
                         ContratoLaboralJPARepository contratoRepo,
                         NominaJPARepository nominaRepo,
                         NominaConfigJPARepository configRepo,
                         EmpresaJPARepository empresaRepo,
                         AfiliacionService afiliacionService,
                         ResolverNovedadesPila resolverNovedades,
                         com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaAportanteConfigRepo aportanteConfigRepo) {
        this.encabezadoRepo = encabezadoRepo;
        this.planillaRepo = planillaRepo;
        this.cotizanteRepo = cotizanteRepo;
        this.contratoRepo = contratoRepo;
        this.nominaRepo = nominaRepo;
        this.configRepo = configRepo;
        this.empresaRepo = empresaRepo;
        this.afiliacionService = afiliacionService;
        this.resolverNovedades = resolverNovedades;
        this.aportanteConfigRepo = aportanteConfigRepo;
    }

    /**
     * Genera la planilla de un período.
     *
     * @param periodo 'YYYY-MM'
     * @return la planilla generada
     */
    @Transactional
    public PilaEncabezadoEntity generar(Integer empresaId, String periodo) {
        YearMonth ym = YearMonth.parse(periodo);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();

        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                        "La empresa no tiene configuración de nómina"));

        List<ContratoLaboralEntity> contratos = contratoRepo.findVigentesEnPeriodo(empresaId, desde, hasta);
        if (contratos.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay contratos vigentes en " + periodo);
        }

        // Validar ANTES de generar: es la diferencia entre un error accionable
        // y un archivo rechazado por el operador sin diagnóstico.
        List<String> problemas = validarTodos(contratos, hasta);
        if (!problemas.isEmpty()) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede generar PILA de " + periodo + ":\n" + String.join("\n", problemas));
        }

        PilaEncabezadoEntity enc = encabezadoRepo.findByEmpresaIdAndPeriodo(empresaId, periodo)
                .orElseGet(PilaEncabezadoEntity::new);

        if (PilaEncabezadoEntity.Estado.PRESENTADA.equals(enc.getEstado())
            || PilaEncabezadoEntity.Estado.PAGADA.equals(enc.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La planilla de " + periodo + " ya fue presentada. Use una corrección.");
        }

        armarEncabezado(enc, empresa, config, empresaId, periodo);
        encabezadoRepo.save(enc);

        PilaPlanillaEntity planilla = armarPlanilla(enc, ym, contratos.size());
        planillaRepo.save(planilla);

        // Reescribir: una regeneración produce el detalle desde cero.
        if (planilla.getId() != null) cotizanteRepo.deleteByPlanillaId(planilla.getId());

        int secuencia = 1;
        BigDecimal totalNomina = BigDecimal.ZERO;

        for (ContratoLaboralEntity c : contratos) {
            // La nómina liquidada del mes: de ahí salen el IBC real y los aportes.
            // Con quincenas pueden ser dos → se consolidan.
            NominaEntity nomina = consolidarNominas(
                    nominaRepo.findByContratoEnRango(c.getId(), desde, hasta));
            PilaCotizanteEntity cot = armarCotizante(planilla, c, nomina, config, secuencia++, desde, hasta);
            cotizanteRepo.save(cot);
            totalNomina = totalNomina.add(cot.getIbcPension());
        }

        planilla.setTotalEmpleados(secuencia - 1);
        planilla.setTotalNomina(totalNomina);
        planillaRepo.save(planilla);

        enc.setEstado(PilaEncabezadoEntity.Estado.GENERADA);
        return encabezadoRepo.save(enc);
    }

    private List<String> validarTodos(List<ContratoLaboralEntity> contratos, LocalDate fecha) {
        List<String> problemas = new ArrayList<>();
        for (ContratoLaboralEntity c : contratos) {
            List<String> p = afiliacionService.validarParaPila(c, fecha);
            if (!p.isEmpty()) {
                String quien = c.getEmpleado() != null
                        ? c.getEmpleado().getNombreCompletoResuelto() : ("contrato " + c.getId());
                p.forEach(x -> problemas.add("• " + quien + ": " + x));
            }
        }
        return problemas;
    }

    // ── Encabezado ──────────────────────────────────────────────────────────

    private void armarEncabezado(PilaEncabezadoEntity enc, EmpresaEntity empresa,
                                 NominaConfigEntity config, Integer empresaId, String periodo) {
        enc.setEmpresaId(empresaId);
        enc.setPeriodo(periodo);
        // Snapshot: la planilla de marzo debe seguir mostrando los datos de marzo.
        enc.setRazonSocial(empresa.getRazonSocial());
        enc.setTipoDocumento("NI");
        enc.setNumeroDocumento(empresa.getNit());
        enc.setDigitoVerificacion(empresa.getDv());
        enc.setTipoPersona("J");
        enc.setTelefono(empresa.getTelefono());
        enc.setCodCiudad(empresa.getMunicipioId() != null ? empresa.getMunicipioId().toString() : null);
        enc.setExoneradoLey1607(Boolean.TRUE.equals(config.getAplicaExoneracion1607()));

        // P4b: datos del aportante para el encabezado (rep. legal, clasificación,
        // actividad económica, operador). Se capturan en pila_aportante_config.
        var apc = aportanteConfigRepo.findById(empresaId).orElse(null);
        if (apc != null) {
            enc.setTipoAportante(apc.getTipoAportante());
            enc.setClaseAportante(apc.getClaseAportante());
            enc.setNaturalezaAportante(apc.getNaturalezaAportante());
            enc.setCodActividadEconomica(apc.getCodActividadEconomica());
            enc.setCodigoOperador(apc.getCodOperador());
            enc.setFormaPresentacion(apc.getFormaPresentacion());
            enc.setRepLegalTipoDocumento(apc.getRepLegalTipoDocumento());
            enc.setRepLegalDocumento(apc.getRepLegalDocumento());
            enc.setRepLegalApellido1(apc.getRepLegalApellido1());
            enc.setRepLegalApellido2(apc.getRepLegalApellido2());
            enc.setRepLegalNombre1(apc.getRepLegalNombre1());
            enc.setRepLegalNombre2(apc.getRepLegalNombre2());
        }
        if (enc.getRepLegalDocumento() == null) {
            log.warn("PILA {}: sin representante legal (configurar en PILA → aportante).", periodo);
        }
    }

    private PilaPlanillaEntity armarPlanilla(PilaEncabezadoEntity enc, YearMonth ym, int totalEmpleados) {
        List<PilaPlanillaEntity> existentes = enc.getId() != null
                ? planillaRepo.findByEncabezadoId(enc.getId()) : List.of();
        PilaPlanillaEntity p = existentes.isEmpty() ? new PilaPlanillaEntity() : existentes.get(0);

        p.setEncabezado(enc);
        p.setTipoRegistro("01");
        p.setSecuencia(1);
        p.setTipoPlanilla("E");   // E = empleados

        // Pensión/ARL/CCF: mes VENCIDO. Salud: mes ANTICIPADO. Son distintos.
        p.setPeriodoPago(ym.toString());
        p.setPeriodoPagoSalud(ym.plusMonths(1).toString());
        p.setTotalEmpleados(totalEmpleados);
        return p;
    }

    /**
     * Consolida las nóminas del mes en una sola vista para el cotizante.
     *
     * <p>Con un solo período (mensual) devuelve esa nómina tal cual. Con
     * quincenas suma devengado, aportes y días, y une las novedades.
     */
    private NominaEntity consolidarNominas(java.util.List<NominaEntity> nominas) {
        if (nominas == null || nominas.isEmpty()) return null;
        if (nominas.size() == 1) return nominas.get(0);

        NominaEntity agg = new NominaEntity();
        BigDecimal devengado = BigDecimal.ZERO;
        BigDecimal salud = BigDecimal.ZERO, pension = BigDecimal.ZERO, arl = BigDecimal.ZERO;
        BigDecimal caja = BigDecimal.ZERO, icbf = BigDecimal.ZERO, sena = BigDecimal.ZERO;
        int dias = 0;
        java.util.List<com.cloud_technological.aura_pos.entity.NominaNovedadEntity> novs =
                new java.util.ArrayList<>();
        for (NominaEntity n : nominas) {
            devengado = devengado.add(nz(n.getTotalDevengado()));
            salud = salud.add(nz(n.getAporteSalud()));
            pension = pension.add(nz(n.getAportePension()));
            arl = arl.add(nz(n.getAporteArl()));
            caja = caja.add(nz(n.getAporteCaja()));
            icbf = icbf.add(nz(n.getAporteIcbf()));
            sena = sena.add(nz(n.getAporteSena()));
            dias += n.getDiasTrabajados() != null ? n.getDiasTrabajados() : 0;
            if (n.getNovedades() != null) novs.addAll(n.getNovedades());
        }
        agg.setTotalDevengado(devengado);
        agg.setAporteSalud(salud);
        agg.setAportePension(pension);
        agg.setAporteArl(arl);
        agg.setAporteCaja(caja);
        agg.setAporteIcbf(icbf);
        agg.setAporteSena(sena);
        agg.setDiasTrabajados(Math.min(dias, 30));  // PILA topa a 30 días/mes
        agg.setNovedades(novs);
        return agg;
    }

    // ── Cotizante ───────────────────────────────────────────────────────────

    private PilaCotizanteEntity armarCotizante(PilaPlanillaEntity planilla,
                                               ContratoLaboralEntity c,
                                               NominaEntity nomina,
                                               NominaConfigEntity config,
                                               int secuencia,
                                               LocalDate desde, LocalDate hasta) {
        PilaCotizanteEntity cot = new PilaCotizanteEntity();
        cot.setPlanilla(planilla);
        cot.setContrato(c);
        cot.setSecuencia(secuencia);

        // ── Identificación (Fase 1) ─────────────────────────────────────────
        TerceroEntity t = c.getEmpleado() != null ? c.getEmpleado().getTercero() : null;
        if (t != null) {
            cot.setTipoDocumento(mapearTipoDocumento(t.getTipoDocumento()));
            cot.setNumeroIdentificacion(t.getNumeroDocumento());
            cot.setApellido1(t.getApellido1());
            cot.setApellido2(t.getApellido2());
            cot.setNombre1(t.getNombre1());
            cot.setNombre2(t.getNombre2());
        }
        cot.setTipoCotizante(c.getTipoCotizante());
        cot.setSubtipoCotizante(c.getSubtipoCotizante());
        cot.setSalarioBasico(c.getSalarioBase());
        cot.setSalarioIntegral(Boolean.TRUE.equals(c.getEsSalarioIntegral()));

        // ── Banderas derivadas (Fases 2 y 5.5) ──────────────────────────────
        NovedadesPila nov = resolverNovedades.resolver(c, nomina, desde, hasta);
        aplicarBanderas(cot, nov);

        // ── Entidades: códigos LITERALES (snapshot) ─────────────────────────
        cot.setCodEps(codigoDe(c, Tipo.EPS, hasta));
        cot.setCodAfp(codigoDe(c, Tipo.AFP, hasta));
        cot.setCodCcf(codigoDe(c, Tipo.CCF, hasta));
        cot.setCodArl(codigoDe(c, Tipo.ARL, hasta));
        cot.setCodEpsTraslado(nov.getCodEpsAnterior());
        cot.setCodAfpTraslado(nov.getCodAfpAnterior());
        cot.setClaseRiesgo(c.getNivelRiesgoArl() != null ? c.getNivelRiesgoArl().toString() : null);

        // ── IBC y días por subsistema ───────────────────────────────────────
        // El IBC es la base de cotización (sin auxilio de transporte ni pagos no
        // salariales), NO el total devengado. La nómina ya la guarda; respaldo a
        // total_devengado solo para nóminas viejas sin `ibc`.
        BigDecimal ibcBase = nomina == null ? BigDecimal.ZERO
                : (nomina.getIbc() != null ? nomina.getIbc() : nz(nomina.getTotalDevengado()));
        int diasPeriodo = nomina != null && nomina.getDiasTrabajados() != null
                ? nomina.getDiasTrabajados() : 30;

        BasesPila bases = BasesPila.builder()
                .ibcBase(ibcBase)
                .smmlv(config.getSmmlv())
                .diasPeriodo(diasPeriodo)
                .novedades(nov)
                .build();

        cot.setIbcPension(bases.getIbcPension());
        cot.setIbcSalud(bases.getIbcSalud());
        cot.setIbcArl(bases.getIbcArl());
        cot.setIbcCcf(bases.getIbcCcf());
        cot.setDiasCotizadosPension(bases.getDiasPension());
        cot.setDiasCotizadosSalud(bases.getDiasSalud());
        cot.setDiasCotizadosArl(bases.getDiasArl());
        cot.setDiasCotizadosCcf(bases.getDiasCcf());

        // B-10 — el aprendiz SENA no cotiza pensión ni parafiscales; en lectiva
        // tampoco ARL (solo salud). Sin este ajuste, PILA reporta IBC de pensión
        // con aporte cero y la validación lo marca como omisión ante la UGPP.
        if ("APRENDIZAJE".equals(c.getTipoContrato())) {
            cot.setIbcPension(BigDecimal.ZERO);
            cot.setDiasCotizadosPension(0);
            cot.setIbcCcf(BigDecimal.ZERO);
            cot.setDiasCotizadosCcf(0);
            if (!"PRACTICA".equals(c.getFase())) {   // lectiva: solo salud
                cot.setIbcArl(BigDecimal.ZERO);
                cot.setDiasCotizadosArl(0);
            }
        }

        // ── Aportes: se toman de la liquidación, NO se recalculan ───────────
        // Recalcularlos aquí haría que PILA y la nómina puedan divergir.
        if (nomina != null) {
            cot.setAporteSalud(nz(nomina.getAporteSalud()));
            cot.setAportePension(nz(nomina.getAportePension()));
            cot.setTotalPension(nz(nomina.getAportePension()));
            cot.setAporteRiesgos(nz(nomina.getAporteArl()));
            cot.setAporteCcf(nz(nomina.getAporteCaja()));
            cot.setAporteIcbf(nz(nomina.getAporteIcbf()));
            cot.setAporteSena(nz(nomina.getAporteSena()));
            cot.setTarifaSalud(config.getPctSaludEmpleador());
            cot.setTarifaPension(config.getPctPensionEmpleador());
            cot.setTarifaRiesgos(nz(c.getTarifaArl()));
            cot.setTarifaCcf(config.getPctCajaCompensacion());
            cot.setTarifaIcbf(config.getPctIcbf());
            cot.setTarifaSena(config.getPctSena());
        }

        cot.setExoneradoLey1607(Boolean.TRUE.equals(config.getAplicaExoneracion1607()));
        return cot;
    }

    private void aplicarBanderas(PilaCotizanteEntity cot, NovedadesPila n) {
        cot.setIng(n.isIng());   cot.setFechaIngreso(n.getFechaIngreso());
        cot.setRet(n.isRet());   cot.setFechaRetiro(n.getFechaRetiro());
        cot.setTde(n.isTde());   cot.setTae(n.isTae());
        cot.setTdp(n.isTdp());   cot.setTap(n.isTap());
        cot.setTdl(n.isTdl());   cot.setTal(n.isTal());
        cot.setTdc(n.isTdc());   cot.setTac(n.isTac());
        cot.setTie(n.isTie());
        cot.setVsp(n.isVsp());   cot.setFechaInicioVsp(n.getFechaInicioVsp());
        cot.setVst(n.isVst());   cot.setFechaInicioVst(n.getFechaInicioVst()); cot.setFechaFinVst(n.getFechaFinVst());
        cot.setSln(n.isSln());   cot.setFechaInicioSln(n.getFechaInicioSln()); cot.setFechaFinSln(n.getFechaFinSln());
        cot.setIge(n.isIge());   cot.setFechaInicioIge(n.getFechaInicioIge()); cot.setFechaFinIge(n.getFechaFinIge());
        cot.setLma(n.isLma());   cot.setFechaInicioLma(n.getFechaInicioLma()); cot.setFechaFinLma(n.getFechaFinLma());
        cot.setVacLr(n.isVacLr()); cot.setFechaInicioVacLr(n.getFechaInicioVacLr()); cot.setFechaFinVacLr(n.getFechaFinVacLr());
        cot.setIrl(n.isIrl());   cot.setFechaInicioIrl(n.getFechaInicioIrl()); cot.setFechaFinIrl(n.getFechaFinIrl());
        cot.setAvp(n.isAvp());
        cot.setVct(n.isVct());   cot.setFechaInicioVct(n.getFechaInicioVct()); cot.setFechaFinVct(n.getFechaFinVct());
        cot.setCorreccion(n.isCorreccion());
        cot.setNoAutorizacionIge(n.getNoAutorizacionIge());
        cot.setNoAutorizacionLma(n.getNoAutorizacionLma());
    }

    private String codigoDe(ContratoLaboralEntity c, String tipo, LocalDate fecha) {
        ContratoAfiliacionEntity a = afiliacionService.vigenteEnFecha(c.getId(), tipo, fecha);
        return a != null ? a.codigoOficial() : null;
    }

    /** Mapea el tipo de documento interno al código UGPP. */
    private String mapearTipoDocumento(String tipo) {
        if (tipo == null) return null;
        return switch (tipo) {
            case "CC" -> "CC";
            case "CE" -> "CE";
            case "PASAPORTE" -> "PA";
            case "NIT" -> "NI";
            default -> tipo;
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
