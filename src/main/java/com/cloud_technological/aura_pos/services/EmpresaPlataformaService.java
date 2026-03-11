package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaResponseDto;
import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.UpdateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface EmpresaPlataformaService {

    DashboardPlataformaDto dashboard();

    PageImpl<EmpresaTableDto> listar(PageableDto<Object> pageable);

    EmpresaPlataformaDto obtenerPorId(Integer id);

    CreateEmpresaResponseDto crear(CreateEmpresaPlataformaDto dto);

    EmpresaPlataformaDto actualizar(Integer id, UpdateEmpresaPlataformaDto dto);

    void suspender(Integer id);

    void activar(Integer id);
}
