package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;
import org.springframework.scheduling.annotation.Async;

import com.cloud_technological.aura_pos.dto.error_log.ErrorLogDetalleDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogGrupoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogPageParamsDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ErrorLogService {

    @Async("errorLogExecutor")
    void registrarAsync(String metodo, String endpoint, int statusCode,
                        String mensaje, String detalle,
                        String username, String ipOrigen);

    PageImpl<ErrorLogTableDto> listar(PageableDto<ErrorLogPageParamsDto> pageable);

    PageImpl<ErrorLogGrupoDto> listarGrupos(PageableDto<ErrorLogPageParamsDto> pageable);

    ErrorLogDetalleDto obtenerPorId(Long id);
}
