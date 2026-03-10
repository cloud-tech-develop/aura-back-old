package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.merma.CreateMermaDetalleDto;
import com.cloud_technological.aura_pos.dto.merma.CreateMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MermaDetalleEntity;
import com.cloud_technological.aura_pos.entity.MermaEntity;
import com.cloud_technological.aura_pos.entity.MotivoMermaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.MermaDetalleMapper;
import com.cloud_technological.aura_pos.mappers.MermaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.merma.MermaJPARepository;
import com.cloud_technological.aura_pos.repositories.merma.MermaQueryRepository;
import com.cloud_technological.aura_pos.repositories.merma_detalle.MermaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.motivo_merma.MotivoMermaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.MermaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;


@Service
public class MermaServiceImpl implements MermaService {
    private final MermaQueryRepository mermaRepository;
    private final MermaJPARepository mermaJPARepository;
    private final MermaDetalleJPARepository detalleJPARepository;
    private final MotivoMermaJPARepository motivoJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final MermaMapper mermaMapper;
    private final MermaDetalleMapper detalleMapper;

    @Autowired
    public MermaServiceImpl(MermaQueryRepository mermaRepository,
            MermaJPARepository mermaJPARepository,
            MermaDetalleJPARepository detalleJPARepository,
            MotivoMermaJPARepository motivoJPARepository,
            ProductoJPARepository productoJPARepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            SucursalJPARepository sucursalJPARepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository,
            MermaMapper mermaMapper,
            MermaDetalleMapper detalleMapper) {
        this.mermaRepository = mermaRepository;
        this.mermaJPARepository = mermaJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.motivoJPARepository = motivoJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.empresaRepository = empresaRepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
        this.mermaMapper = mermaMapper;
        this.detalleMapper = detalleMapper;
    }

    @Override
    public PageImpl<MermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return mermaRepository.listar(pageable, empresaId);
    }

    @Override
    public MermaDto obtenerPorId(Long id, Integer empresaId) {
        MermaEntity entity = mermaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Merma no encontrada"));
        MermaDto dto = mermaMapper.toDto(entity);
        dto.setDetalles(mermaRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public MermaDto crear(CreateMermaDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        MotivoMermaEntity motivo = motivoJPARepository.findByIdAndEmpresaId(dto.getMotivoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Motivo no encontrado"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        // 1. Crear cabecera
        MermaEntity merma = mermaMapper.toEntity(dto);
        merma.setEmpresa(empresa);
        merma.setSucursal(sucursal);
        merma.setUsuario(usuario);
        merma.setMotivo(motivo);
        merma.setFecha(LocalDateTime.now());
        merma.setEstado("APROBADA");
        merma = mermaJPARepository.save(merma);

        BigDecimal costoTotal = BigDecimal.ZERO;

        // 2. Procesar cada detalle
        for (CreateMermaDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // 2.1 Validar stock
            InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + producto.getNombre() + " no tiene inventario en esta sucursal"));

            if (inventario.getStockActual().compareTo(item.getCantidad()) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente para: " + producto.getNombre()
                        + ". Disponible: " + inventario.getStockActual());

            // 2.2 Crear detalle
            MermaDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setMerma(merma);
            detalle.setProducto(producto);

            // 2.3 Manejar lote si aplica
            if (item.getLoteId() != null) {
                LoteEntity lote = loteJPARepository.findById(item.getLoteId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lote no encontrado"));
                detalle.setLote(lote);

                lote.setStockActual(lote.getStockActual().subtract(item.getCantidad()));
                loteJPARepository.save(lote);
            }

            detalleJPARepository.save(detalle);

            // 2.4 Actualizar inventario
            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(item.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            // 2.5 Kardex
            registrarMovimiento(sucursal, producto, detalle.getLote(),
                    item.getCantidad().negate(), saldoAnterior, saldoNuevo,
                    item.getCostoUnitario(), "MERMA", "Merma #" + merma.getId());

            costoTotal = costoTotal.add(item.getCantidad().multiply(item.getCostoUnitario()));
        }

        // 3. Actualizar costo total
        merma.setCostoTotal(costoTotal);
        mermaJPARepository.save(merma);

        return obtenerPorId(merma.getId(), empresaId);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        MermaEntity merma = mermaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Merma no encontrada"));

        if (merma.getEstado().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La merma ya está anulada");

        List<MermaDetalleEntity> detalles = detalleJPARepository.findByMermaId(id);

        for (MermaDetalleEntity detalle : detalles) {
            InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(merma.getSucursal().getId()), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());

            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteJPARepository.save(lote);
            }

            registrarMovimiento(merma.getSucursal(), detalle.getProducto(), detalle.getLote(),
                    detalle.getCantidad(), saldoAnterior, saldoNuevo,
                    detalle.getCostoUnitario(), "ANULACION_MERMA", "Anulación Merma #" + merma.getId());
        }

        merma.setEstado("ANULADA");
        mermaJPARepository.save(merma);
    }

    private void registrarMovimiento(SucursalEntity sucursal, ProductoEntity producto,
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
