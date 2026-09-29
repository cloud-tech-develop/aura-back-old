package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CompraService {
    PageImpl<CompraTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    CompraDto obtenerPorId(Long id, Integer empresaId);

    /**
     * Facturas del proveedor sobre las que se puede emitir una nota crédito,
     * paginadas: un proveedor de años tiene miles y el selector no puede
     * traerlas todas.
     */
    PageImpl<com.cloud_technological.aura_pos.dto.compras.CompraAcreditableDto>
            facturasAcreditables(PageableDto<Object> pageable, Integer empresaId);

    /** Lo que queda por acreditar de cada producto de una factura de compra. */
    java.util.List<com.cloud_technological.aura_pos.dto.compras.CompraAcreditableItemDto>
            itemsAcreditables(Integer empresaId, Long compraId);
    CompraDto crear(CreateCompraDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
    CompraDto actualizar(Long id, CreateCompraDto dto, Integer empresaId, Long usuarioId);
}
