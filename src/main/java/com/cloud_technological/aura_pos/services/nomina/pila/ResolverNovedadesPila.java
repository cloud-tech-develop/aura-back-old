package com.cloud_technological.aura_pos.services.nomina.pila;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ContratoAfiliacionEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EntidadSeguridadSocialEntity.Tipo;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.repositories.nomina.AfiliacionJPARepositories.ContratoAfiliacionRepo;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Deriva las banderas de novedad de PILA (Fase 6).
 *
 * <p>Es el corazón de PILA y la razón de que dependa de todas las fases
 * anteriores. Nada de esto se captura: todo se calcula.
 *
 * <p><b>⚠️ Sin verificar contra un operador real.</b> Las reglas están tomadas
 * del formato UGPP, pero los bordes exactos (¿el día del retiro cuenta?, ¿una
 * incapacidad de 1 día se reporta?) necesitan validación con un contador y
 * pruebas contra el operador.
 */
@Slf4j
@Component
public class ResolverNovedadesPila {

    private final ContratoSalarioHistorialJPARepository historialRepo;
    private final ContratoAfiliacionRepo afiliacionRepo;

    public ResolverNovedadesPila(ContratoSalarioHistorialJPARepository historialRepo,
                                 ContratoAfiliacionRepo afiliacionRepo) {
        this.historialRepo = historialRepo;
        this.afiliacionRepo = afiliacionRepo;
    }

    public NovedadesPila resolver(ContratoLaboralEntity contrato,
                                  NominaEntity nomina,
                                  LocalDate desde,
                                  LocalDate hasta) {
        NovedadesPila n = new NovedadesPila();

        resolverIngresoRetiro(n, contrato, desde, hasta);
        resolverVariacionSalarial(n, contrato, desde, hasta);
        resolverTraslados(n, contrato, desde, hasta);
        resolverAusentismos(n, nomina, desde, hasta);

        return n;
    }

    // ── ING / RET ───────────────────────────────────────────────────────────

    private void resolverIngresoRetiro(NovedadesPila n, ContratoLaboralEntity c,
                                       LocalDate desde, LocalDate hasta) {
        // Ingresó dentro del período: el contrato empezó después del corte inicial.
        if (!c.getFechaInicio().isBefore(desde) && !c.getFechaInicio().isAfter(hasta)) {
            n.setIng(true);
            n.setFechaIngreso(c.getFechaInicio());
        }
        // Se retiró dentro del período.
        if (c.getFechaFin() != null
            && !c.getFechaFin().isBefore(desde) && !c.getFechaFin().isAfter(hasta)) {
            n.setRet(true);
            n.setFechaRetiro(c.getFechaFin());
        }
    }

    // ── VSP: variación permanente de salario ────────────────────────────────

    /**
     * <p><b>Esto es lo que exige la Fase 2.</b> Antes un aumento sobrescribía
     * {@code salario_base} y el dato anterior se perdía — con lo cual esta
     * bandera era literalmente incalculable.
     */
    private void resolverVariacionSalarial(NovedadesPila n, ContratoLaboralEntity c,
                                           LocalDate desde, LocalDate hasta) {
        var cambios = historialRepo.findCambiosEnPeriodo(c.getId(), desde, hasta);
        // El primer registro del histórico es el salario inicial, no una
        // variación: solo cuenta si es posterior al inicio del contrato.
        var variaciones = cambios.stream()
                .filter(h -> h.getFechaDesde().isAfter(c.getFechaInicio()))
                .toList();

        if (!variaciones.isEmpty()) {
            n.setVsp(true);
            n.setFechaInicioVsp(variaciones.get(0).getFechaDesde());
            if (variaciones.size() > 1) {
                log.warn("Contrato {} tuvo {} cambios salariales en {}..{}. PILA solo "
                        + "reporta uno; se usa el primero.", c.getId(), variaciones.size(), desde, hasta);
            }
        }
    }

    // ── Traslados ───────────────────────────────────────────────────────────

    /**
     * Traslados de entidad.
     *
     * <p><b>Esto es lo que exige la Fase 5.5.</b> Las afiliaciones llevan
     * fechas justamente para esto: un cambio de EPS dentro del período es un
     * traslado, y hay que reportar la anterior ({@code tde}) y la nueva
     * ({@code tae}).
     */
    private void resolverTraslados(NovedadesPila n, ContratoLaboralEntity c,
                                   LocalDate desde, LocalDate hasta) {
        List<ContratoAfiliacionEntity> cambios = afiliacionRepo.findCambiosEnPeriodo(c.getId(), desde, hasta);

        for (ContratoAfiliacionEntity a : cambios) {
            // Una afiliación que empieza el mismo día que el contrato es el
            // alta inicial, no un traslado.
            if (!a.getFechaDesde().isAfter(c.getFechaInicio())) continue;

            // Un traslado es DESDE una entidad HACIA otra: exige que exista una
            // afiliación anterior. Sin anterior es un alta inicial (no traslado):
            // marcar solo el "hacia" (tae/tap) generaría un traslado sin
            // administradora anterior, que el operador rechaza.
            switch (a.getTipo()) {
                case Tipo.EPS -> {
                    ContratoAfiliacionEntity anterior = anteriorA(c.getId(), Tipo.EPS, a.getFechaDesde());
                    if (anterior != null) {
                        n.setTde(true);
                        n.setTae(true);
                        n.setCodEpsAnterior(anterior.codigoOficial());
                    }
                }
                case Tipo.AFP -> {
                    ContratoAfiliacionEntity anterior = anteriorA(c.getId(), Tipo.AFP, a.getFechaDesde());
                    if (anterior != null) {
                        n.setTdp(true);
                        n.setTap(true);
                        n.setCodAfpAnterior(anterior.codigoOficial());
                    }
                }
                case Tipo.ARL -> {
                    if (anteriorA(c.getId(), Tipo.ARL, a.getFechaDesde()) != null) {
                        n.setTdl(true);
                        n.setTal(true);
                    }
                }
                case Tipo.CCF -> {
                    if (anteriorA(c.getId(), Tipo.CCF, a.getFechaDesde()) != null) {
                        n.setTdc(true);
                        n.setTac(true);
                    }
                }
                default -> { }
            }
        }
    }

    /** La afiliación que estaba vigente el día anterior al traslado. */
    private ContratoAfiliacionEntity anteriorA(Long contratoId, String tipo, LocalDate fechaTraslado) {
        return afiliacionRepo.findEnFecha(contratoId, tipo, fechaTraslado.minusDays(1)).orElse(null);
    }

    // ── Ausentismos ─────────────────────────────────────────────────────────

    /**
     * Ausentismos desde las novedades.
     *
     * <p>Los días se cuentan <b>recortados al período</b>: una incapacidad que
     * empieza el 28 de marzo y termina el 5 de abril aporta 4 días a marzo y 5
     * a abril, no 9 a cada uno.
     */
    private void resolverAusentismos(NovedadesPila n, NominaEntity nomina,
                                     LocalDate desde, LocalDate hasta) {
        if (nomina == null || nomina.getNovedades() == null) return;

        for (NominaNovedadEntity nov : nomina.getNovedades()) {
            LocalDate ini = nov.getFechaInicio();
            LocalDate fin = nov.getFechaFin();
            if (ini == null) continue;

            LocalDate iniRec = ini.isBefore(desde) ? desde : ini;
            LocalDate finRec = (fin == null || fin.isAfter(hasta)) ? hasta : fin;
            if (iniRec.isAfter(finRec)) continue;   // fuera del período

            int dias = (int) ChronoUnit.DAYS.between(iniRec, finRec) + 1;

            switch (nov.getTipo()) {
                case "INCAPACIDAD" -> {
                    // El subtipo (V119) decide la bandera: general la paga la
                    // EPS (ige), riesgo laboral la paga la ARL (irl). Son
                    // banderas distintas y con IBC distinto.
                    if ("RIESGO_LABORAL".equals(nov.getSubtipo())) {
                        n.setIrl(true);
                        n.setFechaInicioIrl(iniRec);
                        n.setFechaFinIrl(finRec);
                        n.setDiasIrl(n.getDiasIrl() + dias);
                    } else {
                        if (nov.getSubtipo() == null) {
                            log.warn("Novedad {} de tipo INCAPACIDAD sin subtipo. Se asume GENERAL. "
                                    + "Si era de riesgo laboral, PILA la reporta mal.", nov.getId());
                        }
                        n.setIge(true);
                        n.setFechaInicioIge(iniRec);
                        n.setFechaFinIge(finRec);
                        n.setDiasIge(n.getDiasIge() + dias);
                        n.setNoAutorizacionIge(nov.getNumeroAutorizacion());
                    }
                }
                case "LICENCIA_MATERNIDAD" -> {
                    n.setLma(true);
                    n.setFechaInicioLma(iniRec);
                    n.setFechaFinLma(finRec);
                    n.setDiasLma(n.getDiasLma() + dias);
                    n.setNoAutorizacionLma(nov.getNumeroAutorizacion());
                }
                case "LICENCIA_REMUNERADA", "VACACIONES" -> {
                    n.setVacLr(true);
                    n.setFechaInicioVacLr(iniRec);
                    n.setFechaFinVacLr(finRec);
                    n.setDiasVacLr(n.getDiasVacLr() + dias);
                }
                case "LICENCIA_NO_REMUNERADA", "SUSPENSION" -> {
                    // sln: cotiza salud (con piso de 1 SMMLV) pero NO pensión,
                    // ARL ni CCF. Ver BasesPila.
                    n.setSln(true);
                    n.setFechaInicioSln(iniRec);
                    n.setFechaFinSln(finRec);
                    n.setDiasSln(n.getDiasSln() + dias);
                }
                default -> { }
            }
        }
    }
}
