package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
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
import com.cloud_technological.aura_pos.services.ProductoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoQueryRepository productoRepository;
    private final ProductoJPARepository productoJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final CategoriaJPARepository categoriaJPARepository;
    private final MarcaJPARepository marcaJPARepository;
    private final UnidadMedidaJPARepository unidadMedidaRepository;
    private final ProductoMapper productoMapper;

    @Autowired
    public ProductoServiceImpl(ProductoQueryRepository productoRepository,
            ProductoJPARepository productoJPARepository,
            EmpresaJPARepository empresaRepository,
            CategoriaJPARepository categoriaJPARepository,
            MarcaJPARepository marcaJPARepository,
            UnidadMedidaJPARepository unidadMedidaRepository,
            ProductoMapper productoMapper) {
        this.productoRepository = productoRepository;
        this.productoJPARepository = productoJPARepository;
        this.empresaRepository = empresaRepository;
        this.categoriaJPARepository = categoriaJPARepository;
        this.marcaJPARepository = marcaJPARepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.productoMapper = productoMapper;
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
        // Validar código de barras duplicado
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank() &&
                productoRepository.existeCodigoBarras(dto.getCodigoBarras(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está registrado");

        ProductoEntity entity = productoMapper.toEntity(dto);

        // Empresa
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);

        // Categoría (opcional)
        if (dto.getCategoriaId() != null) {
            CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            entity.setCategoria(categoria);
        }

        // Marca (opcional)
        if (dto.getMarcaId() != null) {
            MarcaEntity marca = marcaJPARepository.findByIdAndEmpresaId(dto.getMarcaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Marca no encontrada"));
            entity.setMarca(marca);
        }

        // Unidad de medida (obligatoria)
        UnidadMedidaEntity unidad = unidadMedidaRepository.findById(dto.getUnidadMedidaBaseId())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Unidad de medida no encontrada"));
        entity.setUnidadMedidaBase(unidad);

        return productoMapper.toDto(productoJPARepository.save(entity));
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

        productoMapper.updateEntityFromDto(dto, entity);

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

        return productoMapper.toDto(productoJPARepository.save(entity));
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

        entity.setCodigoBarras(dto.getCodigoBarras());

        return productoMapper.toDto(productoJPARepository.save(entity));
    }
}