package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.AsientoRepositorio;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.OrigenDocumento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter JPA del agregado: mapea el dominio a las entities existentes
 * (no duplica tablas) y asigna el consecutivo de comprobante por prefijo.
 * Los totales vienen del dominio, que ya validó el cuadre en build().
 */
@Component
@RequiredArgsConstructor
public class AsientoRepositorioJpa implements AsientoRepositorio {

    private final AsientoContableJPARepository asientoRepo;
    private final AsientoContableQueryRepository queryRepo;

    @Override
    public boolean existePorOrigen(OrigenDocumento origen, Integer empresaId) {
        return asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId(
                origen.tipoOrigen(), origen.origenId(), empresaId);
    }

    @Override
    public Long guardar(Asiento asiento, Integer empresaId, Integer usuarioId, Long periodoId) {
        String comprobante = queryRepo.siguienteNumeroComprobante(
                empresaId, asiento.prefijoComprobante());

        AsientoContableEntity entity = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(asiento.fecha())
                .descripcion(asiento.descripcion())
                .tipoOrigen(asiento.origen().tipoOrigen())
                .origenId(asiento.origen().origenId())
                .periodoContableId(periodoId)
                .numeroComprobante(comprobante)
                .totalDebito(asiento.totalDebito())
                .totalCredito(asiento.totalCredito())
                .estado(asiento.estado().name())
                .usuarioId(usuarioId)
                .detalles(mapDetalles(asiento.partidas()))
                .build();
        entity.getDetalles().forEach(d -> d.setAsiento(entity));

        return asientoRepo.save(entity).getId();
    }

    private List<AsientoDetalleEntity> mapDetalles(List<Partida> partidas) {
        return partidas.stream()
                .map(p -> AsientoDetalleEntity.builder()
                        .cuentaId(p.cuentaId())
                        .descripcion(p.descripcion())
                        .debito(p.debito())
                        .credito(p.credito())
                        .terceroId(p.terceroId())
                        .centroCostoId(p.centroCostoId())
                        .proyectoId(p.proyectoId())
                        .frenteId(p.frenteId())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}
