package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorTrasladoFondos;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.TrasladoFondosEntity;

import lombok.RequiredArgsConstructor;

/**
 * Traslado de fondos entre cuentas de efectivo: DB cuenta destino · CR cuenta
 * origen. No hay resultado ni tercero — la plata solo cambió de bolsillo — así
 * que el asiento tiene exactamente dos líneas y siempre cuadra.
 *
 * <p>Es el asiento de la constitución y el reembolso de la caja menor
 * (DB 110505 · CR 1105 ó 1110), de la consignación del efectivo del día
 * (DB 1110 · CR 1105) y de los movimientos entre bancos.
 *
 * <p>Nace CONTABILIZADO: lo dispara quien administra los fondos, sobre un
 * documento que ya declaró explícitamente sus dos extremos. No hay nada que un
 * revisor tenga que confirmar después.
 */
@Component
@RequiredArgsConstructor
public class TrasladoFondosGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "TF";

    private final LectorTrasladoFondos traslados;

    @Override
    public String tipoOrigen() {
        return "TRASLADO_FONDOS";
    }

    @Override
    public boolean siempreContabilizado() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorTrasladoFondos.TrasladoFondos t = traslados.cargar(ctx.origenId(), ctx.empresaId());
        String detalle = descripcion(t);

        AsientoBuilder b = Asiento.builder(ctx.origen(), t.fecha())
                .prefijo(PREFIJO)
                .descripcion(detalle);

        return b.debito(t.cuentaDestinoId(), detalle + " (entrada)", t.monto())
                .credito(t.cuentaOrigenId(), detalle + " (salida)", t.monto())
                .build();
    }

    /**
     * La observación del usuario manda sobre la etiqueta del concepto: si se
     * tomó el trabajo de escribir "reposición fondo de la sede norte", eso es
     * lo que debe leerse en el libro auxiliar.
     */
    private static String descripcion(LectorTrasladoFondos.TrasladoFondos t) {
        if (t.observacion() != null && !t.observacion().isBlank()) {
            return t.observacion().trim();
        }
        return switch (t.concepto() != null ? t.concepto() : "") {
            case TrasladoFondosEntity.CONCEPTO_CONSTITUCION_CAJA_MENOR -> "Constitución de caja menor";
            case TrasladoFondosEntity.CONCEPTO_REEMBOLSO_CAJA_MENOR -> "Reembolso de caja menor";
            case TrasladoFondosEntity.CONCEPTO_CONSIGNACION -> "Consignación bancaria";
            default -> "Traslado de fondos";
        };
    }
}
