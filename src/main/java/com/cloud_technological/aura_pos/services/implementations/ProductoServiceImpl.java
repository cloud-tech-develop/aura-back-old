package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;
import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoInventarioDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoListDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoPosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoTableDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateCodigoBarrasDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateProductoDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.MarcaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.UnidadMedidaEntity;
import com.cloud_technological.aura_pos.mappers.ProductoMapper;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.marcas.MarcaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoQueryRepository;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaJPARepository;
import com.cloud_technological.aura_pos.services.CategoriaContableProductoService;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.services.ProductoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProductoServiceImpl implements ProductoService {

    private static final List<String> USOS = List.of("VENTA", "INSUMO", "AMBOS");

    private final ProductoQueryRepository productoRepository;
    private final ProductoJPARepository productoJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final CategoriaJPARepository categoriaJPARepository;
    private final MarcaJPARepository marcaJPARepository;
    private final UnidadMedidaJPARepository unidadMedidaRepository;
    private final ProductoMapper productoMapper;
    private final CategoriaContableProductoService categoriaContableService;
    private final ConsumoComposicionService consumoComposicion;

    @Autowired
    private com.cloud_technological.aura_pos.services.LoteStockService loteStock;

    @Autowired
    public ProductoServiceImpl(ProductoQueryRepository productoRepository,
            ProductoJPARepository productoJPARepository,
            EmpresaJPARepository empresaRepository,
            CategoriaJPARepository categoriaJPARepository,
            MarcaJPARepository marcaJPARepository,
            UnidadMedidaJPARepository unidadMedidaRepository,
            ProductoMapper productoMapper,
            CategoriaContableProductoService categoriaContableService,
            ConsumoComposicionService consumoComposicion) {
        this.productoRepository = productoRepository;
        this.productoJPARepository = productoJPARepository;
        this.empresaRepository = empresaRepository;
        this.categoriaJPARepository = categoriaJPARepository;
        this.marcaJPARepository = marcaJPARepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.productoMapper = productoMapper;
        this.categoriaContableService = categoriaContableService;
        this.consumoComposicion = consumoComposicion;
    }

    @Override
    public PageImpl<ProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return productoRepository.listar(pageable, empresaId);
    }

    @Override
    public ProductoDto obtenerPorId(Long id, Integer empresaId) {
        ProductoEntity entity = productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return productoMapper.toDto(entity);
    }

    @Override
    @Transactional
    public ProductoDto crear(CreateProductoDto dto, Integer empresaId) {

        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank() &&
                productoRepository.existeCodigoBarras(dto.getCodigoBarras(), empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está registrado");
        }
        validarCodigoContraPresentaciones(dto.getCodigoBarras(), empresaId);

        categoriaContableService.validarCuentasProducto(empresaId, dto.getCategoriaContableId(),
                dto.getCuentaIngresoId(), dto.getCuentaCostoId(), dto.getCuentaInventarioId());

        try {
            ProductoEntity entity = productoMapper.toEntity(dto);
            if (entity.getVendePorUnidad() == null)
                entity.setVendePorUnidad(true);
            aplicarUso(entity, dto.getUsoProducto(), "VENTA", dto.getVisibleEnPos());

            EmpresaEntity empresa = empresaRepository.findById(empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
            entity.setEmpresa(empresa);

            if (dto.getCategoriaId() != null) {
                CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
                entity.setCategoria(categoria);
            }

            if (dto.getMarcaId() != null) {
                MarcaEntity marca = marcaJPARepository.findByIdAndEmpresaId(dto.getMarcaId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Marca no encontrada"));
                entity.setMarca(marca);
            }

            UnidadMedidaEntity unidad = unidadMedidaRepository.findById(dto.getUnidadMedidaBaseId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Unidad de medida no encontrada"));
            entity.setUnidadMedidaBase(unidad);

            return productoMapper.toDto(productoJPARepository.save(entity));

        } catch (DataIntegrityViolationException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está registrado");
        }
    }
    @Override
    @Transactional
    public ProductoDto actualizar(Long id, UpdateProductoDto dto, Integer empresaId) {
        ProductoEntity entity = productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Validar código de barras duplicado excluyendo el actual
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank() &&
                productoRepository.existeCodigoBarrasExcluyendo(dto.getCodigoBarras(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está en uso");
        validarCodigoContraPresentaciones(dto.getCodigoBarras(), empresaId);

        categoriaContableService.validarCuentasProducto(empresaId, dto.getCategoriaContableId(),
                dto.getCuentaIngresoId(), dto.getCuentaCostoId(), dto.getCuentaInventarioId());

        // El mapper pisa con null lo que no venga: el uso actual se guarda antes.
        String usoActual = entity.getUsoProducto();
        Boolean vendePorUnidadActual = entity.getVendePorUnidad();
        boolean manejabaLotes = Boolean.TRUE.equals(entity.getManejaLotes());
        Integer mesesGarantiaActual = entity.getMesesGarantia();
        productoMapper.updateEntityFromDto(dto, entity);
        // Igual con la garantía: una pantalla que no la maneja no la borra.
        if (dto.getMesesGarantia() == null)
            entity.setMesesGarantia(mesesGarantiaActual);
        // Las pantallas que no manejan "vende por unidad" no lo mandan: se conserva.
        if (dto.getVendePorUnidad() == null)
            entity.setVendePorUnidad(vendePorUnidadActual != null ? vendePorUnidadActual : true);
        aplicarUso(entity, dto.getUsoProducto(), usoActual, dto.getVisibleEnPos());

        // Categoría
        if (dto.getCategoriaId() != null) {
            CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            entity.setCategoria(categoria);
        } else {
            entity.setCategoria(null);
        }

        // Marca
        if (dto.getMarcaId() != null) {
            MarcaEntity marca = marcaJPARepository.findByIdAndEmpresaId(dto.getMarcaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Marca no encontrada"));
            entity.setMarca(marca);
        } else {
            entity.setMarca(null);
        }

        // Unidad de medida
        UnidadMedidaEntity unidad = unidadMedidaRepository.findById(dto.getUnidadMedidaBaseId())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Unidad de medida no encontrada"));
        entity.setUnidadMedidaBase(unidad);

        ProductoEntity guardado = productoJPARepository.save(entity);
        // El stock que ya tenía no está en ningún lote: pasa a SIN-LOTE para que
        // la suma de los lotes siga siendo el inventario.
        if (!manejabaLotes && Boolean.TRUE.equals(guardado.getManejaLotes())) {
            loteStock.cuadrarSinLote(guardado, empresaId);
        }
        return productoMapper.toDto(guardado);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ProductoEntity entity = productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        productoJPARepository.save(entity);
    }
    @Override
    public List<ProductoListDto> list(Integer empresaId) {
        return productoRepository.list(empresaId);
    }

    @Override
    public List<ProductoListDto> list(Integer empresaId, String search, List<String> usos) {
        return productoRepository.list(empresaId, search, usos);
    }

    @Override
    public List<ProductoPosDto> listarPos(Integer empresaId, Long sucursalId) {
        return productoRepository.listarPos(empresaId, sucursalId);
    }
    @Override
    @Transactional
    public ProductoDto actualizarCodigoBarras(Long id, UpdateCodigoBarrasDto dto, Integer empresaId) {

        ProductoEntity entity = productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Validar que no esté duplicado en otro producto
        if (productoRepository.existeCodigoBarrasExcluyendo(dto.getCodigoBarras(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está en uso por otro producto");
        validarCodigoContraPresentaciones(dto.getCodigoBarras(), empresaId);

        entity.setCodigoBarras(dto.getCodigoBarras());

        return productoMapper.toDto(productoJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoDto generarCodigoBarras(Long id, Integer empresaId) {

        ProductoEntity entity = productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Ya tiene código (generado antes o del proveedor): se reimprime ese mismo.
        if (entity.getCodigoBarras() != null && !entity.getCodigoBarras().isBlank())
            return productoMapper.toDto(entity);

        String codigo = codigoEan13Interno(entity.getId());

        if (productoRepository.existeCodigoBarrasExcluyendo(codigo, empresaId, id)
                || productoRepository.codigoUsadoPorPresentacion(codigo, empresaId))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El código " + codigo + " ya está en uso; asígnelo manualmente desde el producto");

        entity.setCodigoBarras(codigo);
        return productoMapper.toDto(productoJPARepository.save(entity));
    }

    /**
     * EAN-13 de uso interno: prefijo 200 (rango reservado, no lo asigna GS1) +
     * el id del producto + dígito de control. Determinista, así el mismo
     * producto siempre recibe el mismo código.
     */
    private String codigoEan13Interno(Long productoId) {
        if (productoId > 999_999_999L)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El id del producto no cabe en un EAN-13; asigne el código manualmente");

        String base = "200" + String.format("%09d", productoId);
        int suma = 0;
        for (int i = 0; i < 12; i++)
            suma += Character.getNumericValue(base.charAt(i)) * (i % 2 == 0 ? 1 : 3);
        return base + ((10 - (suma % 10)) % 10);
    }

    /** Un código de producto no puede repetir el de una presentación: el escáner no sabría cuál vender. */
    private void validarCodigoContraPresentaciones(String codigoBarras, Integer empresaId) {
        if (codigoBarras != null && !codigoBarras.isBlank()
                && productoRepository.codigoUsadoPorPresentacion(codigoBarras, empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El código " + codigoBarras.trim() + " ya lo usa una presentación de la empresa");
    }

    @Override
    public List<ProductoInventarioDto> buscarInventario(Integer empresaId, Long sucursalId, String search) {
        return productoRepository.buscarInventario(empresaId, sucursalId, search);
    }

    @Override
    public ProductoInventarioDto buscarInventarioPorId(Integer empresaId, Long sucursalId, Long productoId) {
        ProductoInventarioDto producto = productoRepository.buscarInventarioPorId(empresaId, sucursalId, productoId);
        if (producto == null)
            throw new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        return producto;
    }

    @Override
    public ProductoInventarioDto buscarPorCodigo(Integer empresaId, Long sucursalId, String codigo) {
        if (codigo == null || codigo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba o escanee un SKU o código de barras");

        ProductoInventarioDto producto = productoRepository.buscarPorCodigo(empresaId, sucursalId, codigo.trim());
        if (producto == null)
            throw new GlobalException(HttpStatus.NOT_FOUND,
                    "No hay un producto activo con el SKU o código de barras '" + codigo.trim() + "'");
        return producto;
    }

    @Override
    @Transactional
    public List<ConsumoComponenteDto> explosion(Long productoId, BigDecimal cantidad, Long sucursalId,
            Integer empresaId) {
        ProductoEntity padre = productoJPARepository.findByIdAndEmpresaId(productoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        BigDecimal cantidadPadre = cantidad != null && cantidad.signum() > 0 ? cantidad : BigDecimal.ONE;

        return consumoComposicion.explotar(padre.getId(), cantidadPadre, sucursalId).stream()
                .map(consumo -> {
                    ProductoEntity hijo = consumo.componente();
                    ConsumoComponenteDto dto = new ConsumoComponenteDto();
                    dto.setProductoId(hijo.getId());
                    dto.setProductoNombre(hijo.getNombre());
                    dto.setProductoSku(hijo.getSku());
                    dto.setUnidadAbreviatura(hijo.getUnidadMedidaBase() != null
                            ? hijo.getUnidadMedidaBase().getAbreviatura() : null);
                    dto.setCantidad(consumo.cantidad());
                    dto.setCostoUnitario(consumo.costoUnitario());
                    dto.setCostoTotal(consumo.costoTotal());
                    dto.setStockDisponible(consumo.stockDisponible());
                    dto.setPermitirStockNegativo(Boolean.TRUE.equals(hijo.getPermitirStockNegativo()));
                    dto.setSuficiente(consumo.suficiente());
                    return dto;
                })
                .toList();
    }

    /**
     * Fija el uso del producto. Un INSUMO nunca se vende en el POS: se apaga
     * `visible_en_pos` aunque venga prendido, para que las dos banderas no se
     * contradigan.
     */
    private void aplicarUso(ProductoEntity entity, String uso, String usoPorDefecto, Boolean visibleEnPos) {
        String valor = uso != null && !uso.isBlank()
                ? uso.trim().toUpperCase()
                : (usoPorDefecto != null ? usoPorDefecto : "VENTA");

        if (!USOS.contains(valor))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Uso de producto inválido: use VENTA, INSUMO o AMBOS");

        entity.setUsoProducto(valor);
        if ("INSUMO".equals(valor)) {
            entity.setVisibleEnPos(false);
        } else if (visibleEnPos == null) {
            entity.setVisibleEnPos(true);
        }
    }
}
