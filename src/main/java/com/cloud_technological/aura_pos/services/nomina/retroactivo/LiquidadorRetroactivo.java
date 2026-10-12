package com.cloud_technological.aura_pos.services.nomina.retroactivo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaPrestacionDocumentoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaPrestacionDocumentoQueryRepository.Documento;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.RetroactivoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.RetroactivoQueryRepository.NominaPagada;

/**
 * Retroactivo salarial (rediseño R8): por cada nómina periódica ya confirmada de la
 * vinculación dentro del rango del documento, la diferencia entre el salario que regía
 * (historial salarial) y el que se pagó, en la misma proporción de días que se pagaron.
 *
 * <p>Sale como novedades RETROACTIVO (constituyen IBC) que la fila liquida con el motor
 * sin días de salario: así salen también salud, pensión, aportes y provisiones sobre
 * la diferencia. Los períodos ya cubiertos por otro retroactivo confirmado no se repiten.
 */
@Component
public class LiquidadorRetroactivo {

    public static final String ORIGEN = "RETROACTIVO";
    private static final String CLASE = "RETROACTIVO";

    private final NominaPrestacionDocumentoQueryRepository documentos;
    private final RetroactivoQueryRepository queryRepository;
    private final ContratoSalarioHistorialJPARepository historialSalario;

    public LiquidadorRetroactivo(NominaPrestacionDocumentoQueryRepository documentos,
            RetroactivoQueryRepository queryRepository,
            ContratoSalarioHistorialJPARepository historialSalario) {
        this.documentos = documentos;
        this.queryRepository = queryRepository;
        this.historialSalario = historialSalario;
    }

    /** El documento, si es de clase RETROACTIVO (vacío en cualquier otra). */
    public Optional<Documento> documentoRetroactivo(Long documentoId) {
        if (documentoId == null) return Optional.empty();
        return documentos.documento(documentoId).filter(d -> CLASE.equals(d.claseComprobante()));
    }

    /** Novedades RETROACTIVO de la fila (vacía si no hay diferencias por pagar). */
    public List<NominaNovedadEntity> novedades(NominaEntity fila, Documento d) {
        Long contratoId = fila.getContrato().getId();
        LocalDate desde = d.fechaDesde();
        Optional<LocalDate> cubierto = documentos.pagadoHastaEnDocumentos(contratoId, CLASE, d.id());
        if (cubierto.isPresent() && !cubierto.get().isBefore(desde)) desde = cubierto.get().plusDays(1);
        List<NominaNovedadEntity> out = new ArrayList<>();
        if (desde.isAfter(d.fechaHasta())) return out;

        for (NominaPagada n : queryRepository.nominasPagadas(contratoId, desde, d.fechaHasta())) {
            LocalDate ini = n.desde().isBefore(desde) ? desde : n.desde();
            LocalDate fin = n.hasta().isAfter(d.fechaHasta()) ? d.fechaHasta() : n.hasta();
            BigDecimal debido = historialSalario.findEnFecha(contratoId, ini).map(h -> h.getSalario()).orElse(null);
            BigDecimal pagado = n.salarioBase();
            if (debido == null || pagado == null || pagado.signum() <= 0 || debido.compareTo(pagado) <= 0) continue;

            BigDecimal valor = valor(debido, pagado, n, ini, fin);
            if (valor.signum() <= 0) continue;
            NominaNovedadEntity nv = new NominaNovedadEntity();
            nv.setTipo("RETROACTIVO");
            nv.setDescripcion("Retroactivo del " + ini + " al " + fin + ": salario " + pagado.toPlainString()
                    + " → " + debido.toPlainString());
            nv.setFechaInicio(ini);
            nv.setFechaFin(fin);
            nv.setNumeroAutorizacion("NOM-" + n.id());
            nv.setCantidad(BigDecimal.ONE);
            nv.setValorUnitario(valor);
            nv.setValorTotal(valor);
            nv.setDias(0);
            nv.setAfectaDiasSalario(false);
            nv.setEsDeduccion(false);
            nv.setConstituyeIbc(true);
            nv.setNaturaleza("DEVENGADO");
            nv.setOrigen(ORIGEN);
            nv.setEstado("APLICADA");
            out.add(nv);
        }
        return out;
    }

    /**
     * (debido − pagado) × (salario proporcional pagado ÷ salario pagado) × parte del
     * período dentro del rango (días calendario).
     */
    static BigDecimal valor(BigDecimal debido, BigDecimal pagado, NominaPagada n, LocalDate ini, LocalDate fin) {
        BigDecimal proporcionDias = n.salarioProporcional() != null
                ? n.salarioProporcional().divide(pagado, 8, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        long diasPeriodo = ChronoUnit.DAYS.between(n.desde(), n.hasta()) + 1;
        long diasRango = ChronoUnit.DAYS.between(ini, fin) + 1;
        BigDecimal parte = BigDecimal.valueOf(diasRango).divide(BigDecimal.valueOf(diasPeriodo), 8, RoundingMode.HALF_UP);
        return debido.subtract(pagado).multiply(proporcionDias).multiply(parte).setScale(2, RoundingMode.HALF_UP);
    }
}
