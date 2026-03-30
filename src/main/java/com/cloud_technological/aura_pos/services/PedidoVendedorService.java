package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.pedidos_vendedor.CreatePedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorPageableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorTableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.RegistrarCobroPedidoDto;

public interface PedidoVendedorService {

    PageImpl<PedidoVendedorTableDto> listar(PedidoVendedorPageableDto pageable, Integer empresaId);

    PedidoVendedorDto obtenerPorId(Long id, Integer empresaId);

    PedidoVendedorDto crear(CreatePedidoVendedorDto dto, Integer empresaId, Long usuarioId);

    void despachar(Long id, Integer empresaId);

    void registrarCobro(Long id, RegistrarCobroPedidoDto dto, Integer empresaId);

    void anular(Long id, Integer empresaId);
}
