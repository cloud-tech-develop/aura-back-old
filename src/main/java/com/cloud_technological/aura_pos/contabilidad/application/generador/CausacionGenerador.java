package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacion;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;

import lombok.RequiredArgsConstructor;

/**
 * Causación programada (E6): materializa la plantilla del mes. Nace SIEMPRE
 * en BORRADOR — el contador aprueba desde la bandeja de pendientes.
 */
@Component
@RequiredArgsConstructor
public class CausacionGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "CS";

    private final LectorCausacion causaciones;

    @Override
    public String tipoOrigen() {
        return "CAUSACION";
    }

    @Override
    public boolean siempreBorrador() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorCausacion.CausacionContable c = causaciones.cargar(ctx.origenId(), ctx.empresaId());

        var b = Asiento.builder(ctx.origen(), c.fecha())
                .prefijo(PREFIJO)
                .descripcion("Causación " + c.nombre() + " — " + c.periodo());
        for (LectorCausacion.LineaCausacion linea : c.lineas()) {
            b.debito(linea.cuentaId(), linea.descripcion(), linea.debito(), linea.terceroId());
            b.credito(linea.cuentaId(), linea.descripcion(), linea.credito(), linea.terceroId());
        }
        return b.build();
    }
}
