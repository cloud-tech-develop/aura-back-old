package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAjusteBancario;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;

import lombok.RequiredArgsConstructor;

/**
 * Ajuste de conciliación bancaria (E9): línea del extracto sin registro en el
 * libro. Cargo del banco (valor &lt;0) → DB gasto (comisión 530515, GMF 530595
 * o intereses pagados 5305) · CR banco; abono (valor &gt;0) → DB banco · CR
 * ingresos financieros (421005). Nace CONTABILIZADO: lo dispara el contador
 * desde la pantalla de conciliación.
 */
@Component
@RequiredArgsConstructor
public class AjusteBancarioGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "AB";

    private final LectorAjusteBancario ajustes;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "AJUSTE_BANCARIO";
    }

    @Override
    public boolean siempreContabilizado() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorAjusteBancario.AjusteBancario ajuste = ajustes.cargar(ctx.origenId(), ctx.empresaId());
        BigDecimal monto = ajuste.valor().abs();
        boolean cargo = ajuste.valor().signum() < 0;
        Long banco = ajuste.cuentaContableBancoId();
        Long contrapartida = cuentas.resolver(ctx.empresaId(), concepto(ajuste.tipoAjuste(), cargo));

        String detalle = ajuste.descripcion() != null && !ajuste.descripcion().isBlank()
                ? ajuste.descripcion()
                : "Ajuste bancario " + ajuste.tipoAjuste();
        AsientoBuilder b = Asiento.builder(ctx.origen(), ajuste.fecha())
                .prefijo(PREFIJO)
                .descripcion("Conciliación bancaria — " + detalle);
        if (cargo) {
            b.debito(contrapartida, detalle, monto, ajuste.terceroBancoId())
                    .credito(banco, "Cargo del banco según extracto", monto);
        } else {
            b.debito(banco, "Abono del banco según extracto", monto)
                    .credito(contrapartida, detalle, monto, ajuste.terceroBancoId());
        }
        return b.build();
    }

    /** El cargo INTERES son intereses pagados (5305); el abono, ganados (421005). */
    private static ConceptoContable concepto(String tipoAjuste, boolean cargo) {
        return switch (tipoAjuste) {
            case ExtractoLineaEntity.AJUSTE_GASTO_BANCARIO -> ConceptoContable.GASTOS_BANCARIOS;
            case ExtractoLineaEntity.AJUSTE_GMF -> ConceptoContable.GMF;
            case ExtractoLineaEntity.AJUSTE_INTERES -> cargo
                    ? ConceptoContable.GASTOS_FINANCIEROS
                    : ConceptoContable.INGRESOS_FINANCIEROS;
            default -> throw new IllegalStateException("Tipo de ajuste desconocido: " + tipoAjuste);
        };
    }
}
