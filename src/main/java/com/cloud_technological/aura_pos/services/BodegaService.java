package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.bodegas.BodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.BodegaTableDto;
import com.cloud_technological.aura_pos.dto.bodegas.CreateBodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.UpdateBodegaDto;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface BodegaService {

    PageImpl<BodegaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    List<BodegaDto> list(Integer empresaId, Integer sucursalId, boolean soloVenta);

    BodegaTableDto obtenerPorId(Long id, Integer empresaId);

    BodegaTableDto crear(CreateBodegaDto dto, Integer empresaId);

    BodegaTableDto actualizar(Long id, UpdateBodegaDto dto, Integer empresaId);

    void eliminar(Long id, Integer empresaId);

    /**
     * La bodega con la que se va a mover el stock.
     *
     * <p>Si el documento no dice cuál (todo lo que existía antes de V172 y
     * cualquier cliente que aún no manda {@code bodegaId}), devuelve la
     * principal de la sucursal. Es el único punto donde se decide: ningún
     * servicio debe resolverla por su cuenta.
     */
    BodegaEntity resolver(Long bodegaId, Integer sucursalId, Integer empresaId);

    /** Igual que {@link #resolver}, pero exige que la bodega venda (POS). */
    BodegaEntity resolverParaVenta(Long bodegaId, Integer sucursalId, Integer empresaId);
}
