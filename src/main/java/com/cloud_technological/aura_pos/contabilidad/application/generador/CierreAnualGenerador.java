package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCierreAnual;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.CierreAnualEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Cierre de ejercicio (E8):
 * PROVISION_RENTA — DB 5405 · CR 2404 con el valor que DIGITÓ el contador
 * (renta fiscal ≠ contable, el sistema solo sugiere);
 * TRASLADO — al abrir el año, la utilidad (3605 crédito) pasa a resultados
 * acumulados: DB 3605 · CR 3705; la pérdida va al revés.
 * Actos deliberados del contador: nacen CONTABILIZADOS como el cierre mensual.
 */
@Component
@RequiredArgsConstructor
public class CierreAnualGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "CA";

    private final LectorCierreAnual cierres;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "CIERRE_ANUAL";
    }

    @Override
    public boolean siempreContabilizado() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorCierreAnual.OperacionContable op = cierres.cargarOperacion(ctx.origenId(), ctx.empresaId());
        var b = Asiento.builder(ctx.origen(), op.fecha()).prefijo(PREFIJO);

        if (CierreAnualEntity.TIPO_PROVISION_RENTA.equals(op.tipo())) {
            return b.descripcion("Provisión impuesto de renta " + op.anio())
                    .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.GASTO_IMPUESTO_RENTA),
                            "Gasto impuesto de renta " + op.anio(), op.monto())
                    .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.IMPUESTO_RENTA_POR_PAGAR),
                            "Impuesto de renta por pagar " + op.anio(), op.monto())
                    .build();
        }

        Long utilidadEjercicio = cuentas.resolver(ctx.empresaId(), ConceptoContable.UTILIDAD_EJERCICIO);
        Long acumulados = cuentas.resolver(ctx.empresaId(), ConceptoContable.RESULTADOS_ACUMULADOS);
        BigDecimal monto = op.monto().abs();
        if (op.monto().signum() > 0) {
            // Utilidad: 3605 tiene saldo crédito → se debita para trasladarla.
            b.descripcion("Traslado utilidad del ejercicio " + op.anio() + " a resultados acumulados")
                    .debito(utilidadEjercicio, "Cancelación utilidad del ejercicio " + op.anio(), monto)
                    .credito(acumulados, "Resultados de ejercicios anteriores", monto);
        } else {
            b.descripcion("Traslado pérdida del ejercicio " + op.anio() + " a resultados acumulados")
                    .debito(acumulados, "Resultados de ejercicios anteriores", monto)
                    .credito(utilidadEjercicio, "Cancelación pérdida del ejercicio " + op.anio(), monto);
        }
        return b.build();
    }
}
