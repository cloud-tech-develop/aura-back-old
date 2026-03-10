package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;
import java.util.List;
import com.cloud_technological.aura_pos.dto.unidad_medida.CreateUnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedida;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaTableDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UpdateUnidadMedidaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface UnidadMedidaService {
    PageImpl<UnidadMedidaTableDto> listar(PageableDto<Object> pageable);
    UnidadMedidaDto obtenerPorId(Long id);
    UnidadMedidaDto crear(CreateUnidadMedidaDto dto);
    UnidadMedidaDto actualizar(Long id, UpdateUnidadMedidaDto dto);
    void eliminar(Long id);
    List<UnidadMedida> list();
}
