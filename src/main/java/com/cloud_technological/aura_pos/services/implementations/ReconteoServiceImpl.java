package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.reconteo.CreateReconteoDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoDetalleResponseDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoResponseDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoTableDto;
import com.cloud_technological.aura_pos.dto.reconteo.UpdateReconteoDetalleDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ReconteoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ReconteoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.reconteo.ReconteoJPARepository;
import com.cloud_technological.aura_pos.repositories.reconteo.ReconteoQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ReconteoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ReconteoServiceImpl implements ReconteoService {

    private final ReconteoJPARepository reconteoJPARepository;
    private final ReconteoQueryRepository reconteoQueryRepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;

    @Autowired
    public ReconteoServiceImpl(
            ReconteoJPARepository reconteoJPARepository,
            ReconteoQueryRepository reconteoQueryRepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            SucursalJPARepository sucursalJPARepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository) {
        this.reconteoJPARepository = reconteoJPARepository;
        this.reconteoQueryRepository = reconteoQueryRepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.empresaRepository = empresaRepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
    }

    @Override
    public PageImpl<ReconteoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return reconteoQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public ReconteoResponseDto obtenerPorId(Long id, Integer empresaId) {
        ReconteoEntity entity = reconteoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Reconteo no encontrado"));
        return toResponseDto(entity);
    }

    @Override
    @Transactional
    public ReconteoResponseDto crear(CreateReconteoDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        // Verificar que no haya un reconteo activo para esta sucursal
        // (opcional: comentar esta validación si se quieren múltiples simultáneos)

        ReconteoEntity reconteo = new ReconteoEntity();
        reconteo.setEmpresa(empresa);
        reconteo.setSucursal(sucursal);
        reconteo.setEstado("BORRADOR");
        reconteo.setTipo(dto.getTipo() != null ? dto.getTipo() : "TOTAL");
        reconteo.setObservaciones(dto.getObservaciones());
        reconteo.setCreadoPor(usuario);
        reconteo.setFechaInicio(LocalDateTime.now());
        reconteo.setCreatedAt(LocalDateTime.now());
        reconteo.setUpdatedAt(LocalDateTime.now());

        reconteo = reconteoJPARepository.save(reconteo);

        // Obtener todos los inventarios de la sucursal y crear un detalle por cada uno
        List<InventarioEntity> inventarios = inventarioJPARepository.findBySucursalEmpresaId(empresaId)
                .stream()
                .filter(inv -> inv.getSucursal().getId().equals(sucursal.getId()))
                .toList();

        for (InventarioEntity inv : inventarios) {
            ReconteoDetalleEntity detalle = new ReconteoDetalleEntity();
            detalle.setReconteo(reconteo);
            detalle.setProducto(inv.getProducto());
            detalle.setStockSistema(inv.getStockActual());
            detalle.setStockContado(null);
            detalle.setAjusteAplicado(false);
            reconteo.getDetalles().add(detalle);
        }

        reconteoJPARepository.save(reconteo);

        return obtenerPorId(reconteo.getId(), empresaId);
    }

    @Override
    @Transactional
    public ReconteoResponseDto actualizarDetalle(Long reconteoId, Long detalleId,
            UpdateReconteoDetalleDto dto, Integer empresaId) {
        ReconteoEntity reconteo = reconteoJPARepository.findByIdAndEmpresaId(reconteoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Reconteo no encontrado"));

        if ("APROBADO".equals(reconteo.getEstado()) || "ANULADO".equals(reconteo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede modificar un reconteo en estado: " + reconteo.getEstado());

        ReconteoDetalleEntity detalle = reconteo.getDetalles().stream()
                .filter(d -> d.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Detalle no encontrado"));

        detalle.setStockContado(dto.getStockContado());

        if ("BORRADOR".equals(reconteo.getEstado())) {
            reconteo.setEstado("EN_CONTEO");
        }

        reconteo.setUpdatedAt(LocalDateTime.now());
        reconteoJPARepository.save(reconteo);

        return obtenerPorId(reconteoId, empresaId);
    }

    @Override
    @Transactional
    public ReconteoResponseDto aprobar(Long id, Integer empresaId, Long usuarioId) {
        ReconteoEntity reconteo = reconteoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Reconteo no encontrado"));

        if (!"BORRADOR".equals(reconteo.getEstado()) && !"EN_CONTEO".equals(reconteo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se puede aprobar un reconteo en estado BORRADOR o EN_CONTEO");

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        for (ReconteoDetalleEntity detalle : reconteo.getDetalles()) {
            if (detalle.getStockContado() == null) continue;

            BigDecimal diferencia = detalle.getStockContado().subtract(detalle.getStockSistema());
            if (diferencia.compareTo(BigDecimal.ZERO) == 0) continue;

            // Actualizar inventario
            InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(
                            reconteo.getSucursal().getId().longValue(),
                            detalle.getProducto().getId())
                    .orElse(null);

            if (inventario == null) continue;

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(diferencia);

            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            // Actualizar lote si aplica
            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(diferencia));
                loteJPARepository.save(lote);
            }

            // Kardex
            String tipoMovimiento = diferencia.compareTo(BigDecimal.ZERO) > 0
                    ? "RECONTEO_AJUSTE_POSITIVO"
                    : "RECONTEO_AJUSTE_NEGATIVO";

            registrarMovimiento(
                    reconteo.getSucursal(),
                    detalle.getProducto(),
                    detalle.getLote(),
                    diferencia.abs(),
                    saldoAnterior,
                    saldoNuevo,
                    BigDecimal.ZERO,
                    tipoMovimiento,
                    "Reconteo #" + reconteo.getId());

            detalle.setAjusteAplicado(true);
        }

        reconteo.setEstado("APROBADO");
        reconteo.setAprobadoPor(usuario);
        reconteo.setFechaCierre(LocalDateTime.now());
        reconteo.setUpdatedAt(LocalDateTime.now());
        reconteoJPARepository.save(reconteo);

        return obtenerPorId(id, empresaId);
    }

    @Override
    @Transactional
    public ReconteoResponseDto anular(Long id, Integer empresaId) {
        ReconteoEntity reconteo = reconteoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Reconteo no encontrado"));

        if ("APROBADO".equals(reconteo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede anular un reconteo ya aprobado");

        if ("ANULADO".equals(reconteo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El reconteo ya está anulado");

        reconteo.setEstado("ANULADO");
        reconteo.setUpdatedAt(LocalDateTime.now());
        reconteoJPARepository.save(reconteo);

        return obtenerPorId(id, empresaId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ReconteoResponseDto toResponseDto(ReconteoEntity entity) {
        ReconteoResponseDto dto = new ReconteoResponseDto();
        dto.setId(entity.getId());
        dto.setSucursalId(entity.getSucursal() != null ? entity.getSucursal().getId() : null);
        dto.setSucursalNombre(entity.getSucursal() != null ? entity.getSucursal().getNombre() : null);
        dto.setEstado(entity.getEstado());
        dto.setTipo(entity.getTipo());
        dto.setObservaciones(entity.getObservaciones());
        dto.setCreadoPorNombre(entity.getCreadoPor() != null ? entity.getCreadoPor().getUsername() : null);
        dto.setAprobadoPorNombre(entity.getAprobadoPor() != null ? entity.getAprobadoPor().getUsername() : null);
        dto.setFechaInicio(entity.getFechaInicio());
        dto.setFechaCierre(entity.getFechaCierre());

        List<ReconteoDetalleResponseDto> detalles = reconteoQueryRepository.obtenerDetalles(entity.getId());
        dto.setDetalles(detalles);

        return dto;
    }

    private void registrarMovimiento(SucursalEntity sucursal, com.cloud_technological.aura_pos.entity.ProductoEntity producto,
            LoteEntity lote, BigDecimal cantidad, BigDecimal saldoAnterior,
            BigDecimal saldoNuevo, BigDecimal costo, String tipo, String referencia) {
        MovimientoInventarioEntity movimiento = new MovimientoInventarioEntity();
        movimiento.setSucursal(sucursal);
        movimiento.setProducto(producto);
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(saldoNuevo);
        movimiento.setCostoHistorico(costo);
        movimiento.setReferenciaOrigen(referencia);
        movimiento.setCreatedAt(LocalDateTime.now());
        movimientoJPARepository.save(movimiento);
    }
}
