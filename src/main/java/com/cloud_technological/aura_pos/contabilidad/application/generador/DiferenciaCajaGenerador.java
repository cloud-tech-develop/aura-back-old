package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferenciaCaja;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Diferencia del cierre de caja (E3): faltante → DB gasto por diferencia ·
 * CR Caja; sobrante → DB Caja · CR ingreso por sobrante. El dinero contado
 * es el que existe: la caja contable se ajusta a la realidad.
 */
@Component
@RequiredArgsConstructor
public class DiferenciaCajaGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "DC";

    private final LectorDiferenciaCaja turnos;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "DIFERENCIA_CAJA";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorDiferenciaCaja.DiferenciaCaja cierre = turnos.cargar(ctx.origenId(), ctx.empresaId());
        BigDecimal diferencia = ReglasAsiento.nz(cierre.diferencia());
        Long caja = cuentas.resolver(ctx.empresaId(), ConceptoContable.CAJA);

        AsientoBuilder b = Asiento.builder(ctx.origen(), cierre.fecha()).prefijo(PREFIJO);
        if (diferencia.signum() < 0) {
            BigDecimal faltante = diferencia.negate();
            b.descripcion("Faltante de caja — cierre turno #" + ctx.origenId())
                    .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.GASTO_DIFERENCIA_CAJA),
                            "Faltante en cierre de caja", faltante)
                    .credito(caja, "Ajuste de caja por faltante", faltante);
        } else {
            b.descripcion("Sobrante de caja — cierre turno #" + ctx.origenId())
                    .debito(caja, "Ajuste de caja por sobrante", diferencia)
                    .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.INGRESO_SOBRANTE_CAJA),
                            "Sobrante en cierre de caja", diferencia);
        }
        return b.build();
    }
}
