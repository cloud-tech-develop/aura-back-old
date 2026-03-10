package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.caja.CajaDto;
import com.cloud_technological.aura_pos.dto.caja.CajaTableDto;
import com.cloud_technological.aura_pos.dto.caja.CreateCajaDto;
import com.cloud_technological.aura_pos.dto.caja.UpdateCajaDto;
import com.cloud_technological.aura_pos.entity.CajaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.CajaMapper;
import com.cloud_technological.aura_pos.repositories.caja.CajaJPARepository;
import com.cloud_technological.aura_pos.repositories.caja.CajaQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.services.CajaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class CajaServiceImpl implements CajaService{
    private final CajaQueryRepository cajaRepository;
    private final CajaJPARepository cajaJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final CajaMapper cajaMapper;

    @Autowired
    public CajaServiceImpl(CajaQueryRepository cajaRepository,
            CajaJPARepository cajaJPARepository,
            SucursalJPARepository sucursalJPARepository,
            CajaMapper cajaMapper) {
        this.cajaRepository = cajaRepository;
        this.cajaJPARepository = cajaJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.cajaMapper = cajaMapper;
    }

    @Override
    public PageImpl<CajaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return cajaRepository.listar(pageable, empresaId);
    }

    @Override
    public CajaDto obtenerPorId(Long id, Integer empresaId) {
        CajaEntity entity = cajaJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Caja no encontrada"));
        return cajaMapper.toDto(entity);
    }

    @Override
    @Transactional
    public CajaDto crear(CreateCajaDto dto, Integer empresaId) {
        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        CajaEntity entity = cajaMapper.toEntity(dto);
        entity.setSucursal(sucursal);
        return cajaMapper.toDto(cajaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public CajaDto actualizar(Long id, UpdateCajaDto dto, Integer empresaId) {
        CajaEntity entity = cajaJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Caja no encontrada"));

        cajaMapper.updateEntityFromDto(dto, entity);
        return cajaMapper.toDto(cajaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        CajaEntity entity = cajaJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Caja no encontrada"));

        entity.setActiva(false);
        cajaJPARepository.save(entity);
    }
}
