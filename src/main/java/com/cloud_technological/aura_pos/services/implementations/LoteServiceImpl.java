package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.inventario.CreateLoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.LoteMapper;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class LoteServiceImpl implements LoteService {
    private final LoteQueryRepository loteRepository;
    private final LoteJPARepository loteJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final LoteMapper loteMapper;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.inventario.LoteAjusteJPARepository loteAjusteRepository;

    @Autowired
    public LoteServiceImpl(LoteQueryRepository loteRepository,
            LoteJPARepository loteJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            LoteMapper loteMapper) {
        this.loteRepository = loteRepository;
        this.loteJPARepository = loteJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.loteMapper = loteMapper;
    }

    @Override
    public PageImpl<LoteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return loteRepository.listar(pageable, empresaId);
    }

    @Override
    public LoteDto obtenerPorId(Long id, Integer empresaId) {
        LoteEntity entity = loteJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lote no encontrado"));
        return loteMapper.toDto(entity);
    }

    @Override
    public List<LoteTableDto> listarPorVencer(Integer empresaId) {
        return loteRepository.listarPorVencer(empresaId);
    }

    @Override
    public List<com.cloud_technological.aura_pos.dto.inventario.VencimientoLoteDto> vencimientos(Integer empresaId, Long sucursalId,
            Integer dias) {
        if (dias != null && (dias < 0 || dias > 3650))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días tienen que estar entre 0 y 3650");
        return loteRepository.vencimientos(empresaId, sucursalId, dias);
    }

    @Override
    public List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId, Integer empresaId) {
        return loteRepository.listarDisponiblesPorProducto(productoId, sucursalId, empresaId);
    }

    @Override
    @Transactional
    public LoteDto crear(CreateLoteDto dto, Integer empresaId) {
        // Crear un lote con stock desde aquí dejaba un número que no estaba en el
        // inventario ni en el kardex. Los lotes nacen con la compra.
        throw new GlobalException(HttpStatus.BAD_REQUEST,
                "Los lotes se crean al registrar la compra, con su código y vencimiento. "
                        + "Aquí solo se corrigen.");
    }

    @Override
    @Transactional
    public LoteDto actualizar(Long id, com.cloud_technological.aura_pos.dto.inventario.UpdateLoteDto dto,
            Integer empresaId, Long usuarioId) {
        LoteEntity lote = loteJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lote no encontrado"));

        String codigo = dto.getCodigoLote().trim();
        String motivo = dto.getMotivo().trim();

        if (!codigo.equalsIgnoreCase(lote.getCodigoLote() != null ? lote.getCodigoLote().trim() : "")) {
            boolean repetido = loteJPARepository
                    .buscarPorCodigo(lote.getProducto().getId(), lote.getBodega().getId(), codigo)
                    .stream().anyMatch(o -> !o.getId().equals(lote.getId()));
            if (repetido)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Ya existe el lote " + codigo + " para este producto en la bodega");
        }
        if (!codigo.equals(lote.getCodigoLote())) {
            registrarAjuste(lote, empresaId, usuarioId, "codigo_lote", lote.getCodigoLote(), codigo, motivo);
            lote.setCodigoLote(codigo);
        }
        if (!java.util.Objects.equals(dto.getFechaVencimiento(), lote.getFechaVencimiento())) {
            registrarAjuste(lote, empresaId, usuarioId, "fecha_vencimiento",
                    lote.getFechaVencimiento() != null ? lote.getFechaVencimiento().toString() : null,
                    dto.getFechaVencimiento() != null ? dto.getFechaVencimiento().toString() : null, motivo);
            lote.setFechaVencimiento(dto.getFechaVencimiento());
        }
        return loteMapper.toDto(loteJPARepository.save(lote));
    }

    private void registrarAjuste(LoteEntity lote, Integer empresaId, Long usuarioId, String campo,
            String anterior, String nuevo, String motivo) {
        com.cloud_technological.aura_pos.entity.LoteAjusteEntity ajuste =
                new com.cloud_technological.aura_pos.entity.LoteAjusteEntity();
        ajuste.setLoteId(lote.getId());
        ajuste.setEmpresaId(empresaId);
        ajuste.setUsuarioId(usuarioId);
        ajuste.setCampo(campo);
        ajuste.setValorAnterior(anterior);
        ajuste.setValorNuevo(nuevo);
        ajuste.setMotivo(motivo);
        loteAjusteRepository.save(ajuste);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        LoteEntity entity = loteJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lote no encontrado"));

        // Desactivar un lote con stock sacaba ese stock de los lotes pero no del
        // inventario: el cuadre se rompía.
        if (entity.getStockActual() != null && entity.getStockActual().signum() != 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El lote " + entity.getCodigoLote() + " todavía tiene stock: sácalo con una merma, "
                            + "un consumo interno o una nota crédito antes de desactivarlo.");

        entity.setActivo(false);
        loteJPARepository.save(entity);
    }

    @Autowired
    private com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository empresaRepository;

    @Override
    public com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto obtenerReglas(Integer empresaId) {
        var empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        var dto = new com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto();
        dto.setBloquearVencidos(!Boolean.FALSE.equals(empresa.getLotesBloquearVencidos()));
        dto.setDiasAlerta(empresa.getLotesDiasAlerta() != null ? empresa.getLotesDiasAlerta() : 30);
        return dto;
    }

    @Override
    @Transactional
    public com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto guardarReglas(
            com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto dto, Integer empresaId) {
        var empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        empresa.setLotesBloquearVencidos(dto.getBloquearVencidos());
        empresa.setLotesDiasAlerta(dto.getDiasAlerta());
        empresaRepository.save(empresa);
        return obtenerReglas(empresaId);
    }
}
