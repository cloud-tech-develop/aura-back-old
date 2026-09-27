package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.CreateReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaTableDto;

public interface ReciboCajaService {

    ReciboCajaDto crear(CreateReciboCajaDto dto, Integer empresaId, Long usuarioId);

    ReciboCajaDto obtener(Long id, Integer empresaId);

    PageImpl<ReciboCajaTableDto> listar(Integer empresaId, Long terceroId, String estado, String search,
            int page, int rows);

    ReciboCajaDto anular(Long id, String motivo, Integer empresaId, Long usuarioId);

    FichaClienteCarteraDto ficha(Long terceroId, Integer empresaId);
}
