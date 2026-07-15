package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCierreAnual;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Distribución de utilidades post-asamblea (E8): DB 3705 por el total
 * distribuido · CR 330505 (reserva legal) · CR 2360 (dividendos decretados).
 * Acto deliberado del contador tras el acta: nace CONTABILIZADO.
 */
@Component
@RequiredArgsConstructor
public class DistribucionUtilidadGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "DU";

    private final LectorCierreAnual cierres;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "DISTRIBUCION_UTILIDAD";
    }

    @Override
    public boolean siempreContabilizado() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorCierreAnual.DistribucionContable d = cierres.cargarDistribucion(ctx.origenId(), ctx.empresaId());
        var reserva = ReglasAsiento.nz(d.reservaLegal());
        var dividendos = ReglasAsiento.nz(d.dividendos());

        return Asiento.builder(ctx.origen(), d.fecha())
                .prefijo(PREFIJO)
                .descripcion("Distribución de utilidades " + d.anio())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.RESULTADOS_ACUMULADOS),
                        "Distribución de utilidades " + d.anio(), reserva.add(dividendos))
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.RESERVA_LEGAL),
                        "Apropiación reserva legal " + d.anio(), reserva)
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.DIVIDENDOS_POR_PAGAR),
                        "Dividendos decretados " + d.anio(), dividendos)
                .build();
    }
}
