package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.compras.CreateGastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.services.GastoService;

@Service
public class GastoServiceImpl implements GastoService {

    @Autowired private GastoJPARepository gastoJPARepository;
    @Autowired private GastoQueryRepository gastoQueryRepository;
    @Autowired private EmpresaJPARepository empresaJPARepository;
    @Autowired private SucursalJPARepository sucursalJPARepository;
    @Autowired private UsuarioJPARepository usuarioJPARepository;

    @Override
    @Transactional
    public GastoDto crear(CreateGastoDto dto, Integer empresaId, Long usuarioId) {
        var empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElse(null);

        GastoEntity gasto = new GastoEntity();
        gasto.setEmpresa(empresa);
        gasto.setSucursal(sucursal);
        gasto.setUsuario(usuario);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        gasto.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now());
        gasto.setDeducible(dto.getDeducible());
        gasto.setEstado("ACTIVO");
        gasto.setCreatedAt(LocalDateTime.now());
        mapCamposTributarios(dto, gasto);

        gasto = gastoJPARepository.save(gasto);
        return toDto(gasto);
    }

    @Override
    @Transactional
    public GastoDto actualizar(Long id, CreateGastoDto dto, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        gasto.setSucursal(sucursal);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        if (dto.getFecha() != null) gasto.setFecha(dto.getFecha());
        gasto.setDeducible(dto.getDeducible());
        mapCamposTributarios(dto, gasto);

        gasto = gastoJPARepository.save(gasto);
        return toDto(gasto);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        gasto.setEstado("ELIMINADO");
        gastoJPARepository.save(gasto);
    }

    @Override
    public PageImpl<GastoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return gastoQueryRepository.listar(pageable, empresaId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void mapCamposTributarios(CreateGastoDto dto, GastoEntity gasto) {
        gasto.setTerceroId(dto.getTerceroId());
        gasto.setCuentaContableId(dto.getCuentaContableId());
        gasto.setCentroCostoId(dto.getCentroCostoId());
        gasto.setPeriodoContableId(dto.getPeriodoContableId());
        gasto.setBaseIva(nvl(dto.getBaseIva()));
        gasto.setTarifaIva(nvl(dto.getTarifaIva()));
        gasto.setValorIva(nvl(dto.getValorIva()));
        gasto.setBaseRetefuente(nvl(dto.getBaseRetefuente()));
        gasto.setTarifaRetefuente(nvl(dto.getTarifaRetefuente()));
        gasto.setValorRetefuente(nvl(dto.getValorRetefuente()));
        gasto.setBaseReteica(nvl(dto.getBaseReteica()));
        gasto.setTarifaReteica(nvl(dto.getTarifaReteica()));
        gasto.setValorReteica(nvl(dto.getValorReteica()));
        gasto.setTipoDocSoporte(dto.getTipoDocSoporte());
        gasto.setNumeroDocSoporte(dto.getNumeroDocSoporte());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private GastoDto toDto(GastoEntity g) {
        GastoDto dto = new GastoDto();
        dto.setId(g.getId());
        dto.setEmpresaId(g.getEmpresa() != null ? g.getEmpresa().getId() : null);
        dto.setSucursalId(g.getSucursal() != null ? g.getSucursal().getId().longValue() : null);
        dto.setSucursalNombre(g.getSucursal() != null ? g.getSucursal().getNombre() : null);
        dto.setUsuarioId(g.getUsuario() != null ? g.getUsuario().getId() : null);
        dto.setUsuarioNombre(g.getUsuario() != null ? g.getUsuario().getUsername() : null);
        dto.setCategoria(g.getCategoria());
        dto.setDescripcion(g.getDescripcion());
        dto.setMonto(g.getMonto());
        dto.setFecha(g.getFecha());
        dto.setDeducible(g.getDeducible());
        dto.setEstado(g.getEstado());
        dto.setCreatedAt(g.getCreatedAt());
        // Campos tributarios
        dto.setTerceroId(g.getTerceroId());
        dto.setCuentaContableId(g.getCuentaContableId());
        dto.setCentroCostoId(g.getCentroCostoId());
        dto.setPeriodoContableId(g.getPeriodoContableId());
        dto.setBaseIva(g.getBaseIva());
        dto.setTarifaIva(g.getTarifaIva());
        dto.setValorIva(g.getValorIva());
        dto.setBaseRetefuente(g.getBaseRetefuente());
        dto.setTarifaRetefuente(g.getTarifaRetefuente());
        dto.setValorRetefuente(g.getValorRetefuente());
        dto.setBaseReteica(g.getBaseReteica());
        dto.setTarifaReteica(g.getTarifaReteica());
        dto.setValorReteica(g.getValorReteica());
        dto.setTipoDocSoporte(g.getTipoDocSoporte());
        dto.setNumeroDocSoporte(g.getNumeroDocSoporte());
        return dto;
    }
}
