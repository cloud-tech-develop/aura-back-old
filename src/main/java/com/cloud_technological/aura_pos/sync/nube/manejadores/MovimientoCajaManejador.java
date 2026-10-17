package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.caja.CreateMovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.MovimientoCajaDto;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.services.TurnoCajaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Ingreso o egreso de caja hecho en la tienda.
 *
 * <p>Payload: {@code {turnoId, movimiento: CreateMovimientoCajaDto,
 * ventaIdCuentaCobrar}}. El abono a una cuenta por cobrar creada en la tienda
 * no tiene id en la nube: se ubica por la venta que la originó.
 */
@Component
@RequiredArgsConstructor
public class MovimientoCajaManejador implements ManejadorComando {

    private final TurnoCajaService turnoService;
    private final CuentaCobrarJPARepository cuentasCobrar;
    private final TraductorIds ids;
    private final SyncNubeQueryRepository repo;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.MOVIMIENTO_CAJA;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        JsonNode p = c.getPayload();
        CreateMovimientoCajaDto dto;
        try {
            dto = objectMapper.treeToValue(p.get("movimiento"), CreateMovimientoCajaDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Movimiento ilegible en el comando: " + e.getMessage(), e);
        }
        Long turnoId = ids.aNube(nodo.getId(), TiposComando.T_TURNO, p.path("turnoId").asLong());

        if (p.hasNonNull("ventaIdCuentaCobrar")) {
            Long ventaId = ids.aNube(nodo.getId(), TiposComando.T_VENTA, p.get("ventaIdCuentaCobrar").asLong());
            CuentaCobrarEntity cxc = cuentasCobrar.findByVentaIdAndEmpresaId(ventaId, nodo.getEmpresaId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                            "La venta " + ventaId + " no tiene cuenta por cobrar en la nube"));
            dto.setCuentaCobrarId(cxc.getId());
        } else {
            dto.setCuentaCobrarId(TraductorIds.soloNube(dto.getCuentaCobrarId()));
        }
        dto.setCuentaPagarId(TraductorIds.soloNube(dto.getCuentaPagarId()));

        MovimientoCajaDto creado = turnoService.registrarMovimiento(turnoId, dto, nodo.getEmpresaId(), c.getUsuarioId());
        if (creado.getId() != null && c.getIdLocal() != null)
            repo.guardarMapa(nodo.getId(), TiposComando.T_MOVIMIENTO_CAJA, c.getIdLocal(), creado.getId());
        return Salida.de(creado.getId());
    }
}
