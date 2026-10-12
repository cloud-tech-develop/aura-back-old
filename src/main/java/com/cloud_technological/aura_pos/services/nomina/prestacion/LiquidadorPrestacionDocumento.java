package com.cloud_technological.aura_pos.services.nomina.prestacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.LiquidacionPrestacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaPrestacionDocumentoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaPrestacionDocumentoQueryRepository.Documento;
import com.cloud_technological.aura_pos.services.implementations.ConceptoNominaService;
import com.cloud_technological.aura_pos.services.nomina.FormulasPrestaciones;
import com.cloud_technological.aura_pos.services.nomina.LineaLiquidacion;
import com.cloud_technological.aura_pos.services.nomina.PromedioSalarialService;
import com.cloud_technological.aura_pos.services.nomina.Traza;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasAprendiz;

/**
 * Prima, cesantías e intereses de cesantías como comprobante propio (rediseño R6).
 *
 * <p>Cada empleado del documento se liquida desde el día siguiente al último pago de
 * esa prestación (en una nómina por documento confirmada o en el módulo de
 * Prestaciones), o desde su ingreso, hasta el fin del período del documento (o su
 * retiro). La base lleva el promedio del salario variable; las fórmulas son las de
 * {@link FormulasPrestaciones}.
 */
@Component
public class LiquidadorPrestacionDocumento {

    /** Clase del comprobante → tipo del módulo de Prestaciones, concepto y nombre. */
    private record Prestacion(String tipo, String concepto, String nombre) {
    }

    private static final Map<String, Prestacion> POR_CLASE = Map.of(
            "PRIMA", new Prestacion("PRIMA", "PRIMA", "Prima de servicios"),
            "CESANTIAS", new Prestacion("CESANTIAS", "CESANTIAS", "Cesantías"),
            "INTERESES_CESANTIAS", new Prestacion("INTERESES_CESANTIAS", "INT_CESANTIAS", "Intereses de cesantías"));

    private final NominaPrestacionDocumentoQueryRepository queryRepository;
    private final LiquidacionPrestacionJPARepository prestaciones;
    private final ContratoSalarioHistorialJPARepository historialSalario;
    private final PromedioSalarialService promedios;
    private final ConceptoNominaService conceptos;

    public LiquidadorPrestacionDocumento(NominaPrestacionDocumentoQueryRepository queryRepository,
            LiquidacionPrestacionJPARepository prestaciones,
            ContratoSalarioHistorialJPARepository historialSalario,
            PromedioSalarialService promedios,
            ConceptoNominaService conceptos) {
        this.queryRepository = queryRepository;
        this.prestaciones = prestaciones;
        this.historialSalario = historialSalario;
        this.promedios = promedios;
        this.conceptos = conceptos;
    }

    /** El documento, si es de prima, cesantías o intereses (vacío en cualquier otra clase). */
    public Optional<Documento> documentoDePrestacion(Long documentoId) {
        if (documentoId == null) return Optional.empty();
        return queryRepository.documento(documentoId).filter(d -> POR_CLASE.containsKey(d.claseComprobante()));
    }

    /**
     * La línea de la prestación del empleado.
     *
     * @return vacío si ya se le pagó todo el período (o el contrato no tiene prestaciones)
     * @throws GlobalException 400 si el contrato no tiene prestaciones laborales
     */
    public Optional<LineaLiquidacion> linea(NominaEntity fila, Documento d, NominaConfigEntity config,
            Integer empresaId) {
        ContratoLaboralEntity contrato = fila.getContrato();
        // Servicios y aprendiz en etapa lectiva no causan prestaciones (Ley 2466/2025).
        if (!ReglasAprendiz.causaPrestaciones(contrato))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    ReglasAprendiz.esAprendiz(contrato)
                            ? "El aprendiz en etapa lectiva no tiene prima ni cesantías."
                            : "Un contrato de prestación de servicios no tiene prima ni cesantías.");

        Prestacion p = POR_CLASE.get(d.claseComprobante());
        LocalDate desde = desde(fila, d, p, empresaId);
        LocalDate hasta = contrato.getFechaFin() != null && contrato.getFechaFin().isBefore(d.fechaHasta())
                ? contrato.getFechaFin() : d.fechaHasta();
        if (desde.isAfter(hasta)) return Optional.empty();

        int dias = FormulasPrestaciones.diasBase360(desde, hasta);
        BigDecimal salario = historialSalario.findEnFecha(contrato.getId(), hasta)
                .map(h -> h.getSalario())
                .filter(s -> s != null && s.signum() > 0)
                .orElse(contrato.getSalarioBase() != null ? contrato.getSalarioBase() : BigDecimal.ZERO);
        BigDecimal auxilio = auxilio(config, salario);
        BigDecimal promedio = promedios.promedioMensualVariable(empresaId, fila.getEmpleado().getId(), desde, hasta,
                dias, false);
        BigDecimal valor = FormulasPrestaciones.valor(p.tipo(), salario, auxilio, promedio, dias, false);
        if (valor.signum() <= 0) return Optional.empty();

        ConceptoNominaEntity c = conceptos.porCodigo(empresaId, p.concepto(), hasta);
        Traza t = new Traza()
                .paso(p.nombre() + " del " + desde + " al " + hasta, "")
                .paso("Días (base 360)", BigDecimal.valueOf(dias))
                .paso("Salario", salario)
                .paso("Auxilio de transporte", auxilio)
                .paso("Promedio variable mensual", promedio)
                .paso("Fórmula", "INTERESES_CESANTIAS".equals(p.tipo())
                        ? "cesantías del período × 12 % × días ÷ 360" : "(salario + auxilio + promedio) × días ÷ 360")
                .paso("Valor", valor);
        LineaLiquidacion l = LineaLiquidacion.devengado(c, valor, t);
        l.setCantidad(BigDecimal.valueOf(dias));
        return Optional.of(l);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /**
     * Desde: lo más tarde entre el inicio del período, el ingreso y el día siguiente al
     * último pago (nómina por documento confirmada o módulo de Prestaciones).
     */
    private LocalDate desde(NominaEntity fila, Documento d, Prestacion p, Integer empresaId) {
        LocalDate desde = d.fechaDesde();
        LocalDate ingreso = fila.getContrato().getFechaInicio();
        if (ingreso.isAfter(desde)) desde = ingreso;
        Optional<LocalDate> enDocumentos = queryRepository.pagadoHastaEnDocumentos(fila.getContrato().getId(),
                d.claseComprobante(), d.id());
        Optional<LocalDate> enPrestaciones = prestaciones
                .findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoNotOrderByFechaHastaDesc(
                        empresaId, fila.getEmpleado().getId(), p.tipo(), "ANULADA")
                .map(u -> u.getFechaHasta());
        for (Optional<LocalDate> pagado : List.of(enDocumentos, enPrestaciones)) {
            if (pagado.isPresent() && !pagado.get().plusDays(1).isBefore(desde)) desde = pagado.get().plusDays(1);
        }
        return desde;
    }

    /** Auxilio de transporte si el salario no pasa de 2 SMMLV (modo de nómina completo). */
    private static BigDecimal auxilio(NominaConfigEntity config, BigDecimal salario) {
        if (config == null || !"COMPLETO".equals(config.getModoNomina())) return BigDecimal.ZERO;
        BigDecimal smmlv = config.getSmmlv() != null ? config.getSmmlv() : BigDecimal.ZERO;
        BigDecimal dos = smmlv.multiply(BigDecimal.valueOf(2));
        if (dos.signum() > 0 && salario.compareTo(dos) <= 0)
            return config.getAuxilioTransporte() != null ? config.getAuxilioTransporte() : BigDecimal.ZERO;
        return BigDecimal.ZERO;
    }
}
