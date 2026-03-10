package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.DescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioVolumenDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.DescuentoClienteEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PrecioClienteEntity;
import com.cloud_technological.aura_pos.entity.PrecioVolumenEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.mappers.PrecioDinamicoMapper;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.DescuentoClienteJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.DescuentoClienteQueryRepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.PrecioClienteJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.PrecioClienteQueryRepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.PrecioVolumenJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_dinamicos.PrecioVolumenQueryRepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.PrecioDinamicoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class PrecioDinamicoServiceImpl implements PrecioDinamicoService {

    // Repositories - Precio Cliente
    private final PrecioClienteJPARepository precioClienteJPARepository;
    private final PrecioClienteQueryRepository precioClienteQueryRepository;
    
    // Repositories - Descuento Cliente
    private final DescuentoClienteJPARepository descuentoClienteJPARepository;
    private final DescuentoClienteQueryRepository descuentoClienteQueryRepository;
    
    // Repositories - Precio Volumen
    private final PrecioVolumenJPARepository precioVolumenJPARepository;
    private final PrecioVolumenQueryRepository precioVolumenQueryRepository;
    
    // Repositories - Entidades relacionadas
    private final EmpresaJPARepository empresaJPARepository;
    private final TerceroJPARepository terceroJPARepository;
    private final CategoriaJPARepository categoriaJPARepository;
    private final ProductoPresentacionJPARepository productoPresentacionJPARepository;
    
    // Mapper
    private final PrecioDinamicoMapper mapper;

    @Autowired
    public PrecioDinamicoServiceImpl(
            PrecioClienteJPARepository precioClienteJPARepository,
            PrecioClienteQueryRepository precioClienteQueryRepository,
            DescuentoClienteJPARepository descuentoClienteJPARepository,
            DescuentoClienteQueryRepository descuentoClienteQueryRepository,
            PrecioVolumenJPARepository precioVolumenJPARepository,
            PrecioVolumenQueryRepository precioVolumenQueryRepository,
            EmpresaJPARepository empresaJPARepository,
            TerceroJPARepository terceroJPARepository,
            CategoriaJPARepository categoriaJPARepository,
            ProductoPresentacionJPARepository productoPresentacionJPARepository,
            PrecioDinamicoMapper mapper) {
        this.precioClienteJPARepository = precioClienteJPARepository;
        this.precioClienteQueryRepository = precioClienteQueryRepository;
        this.descuentoClienteJPARepository = descuentoClienteJPARepository;
        this.descuentoClienteQueryRepository = descuentoClienteQueryRepository;
        this.precioVolumenJPARepository = precioVolumenJPARepository;
        this.precioVolumenQueryRepository = precioVolumenQueryRepository;
        this.empresaJPARepository = empresaJPARepository;
        this.terceroJPARepository = terceroJPARepository;
        this.categoriaJPARepository = categoriaJPARepository;
        this.productoPresentacionJPARepository = productoPresentacionJPARepository;
        this.mapper = mapper;
    }

    // ========== PRECIO CLIENTE ==========

    @Override
    public PageImpl<PrecioClienteTableDto> listarPreciosCliente(PageableDto<Object> pageable, Integer empresaId) {
        return precioClienteQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public PrecioClienteDto obtenerPrecioClientePorId(Long id, Integer empresaId) {
        PrecioClienteEntity entity = precioClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio especial no encontrado"));
        return mapper.toDto(entity);
    }

    @Override
    public List<PrecioClienteTableDto> listarPreciosClientePorTercero(Integer empresaId, Long terceroId) {
        return precioClienteQueryRepository.listarPorCliente(empresaId, terceroId);
    }

    @Override
    @Transactional
    public PrecioClienteDto crearPrecioCliente(CreatePrecioClienteDto dto, Integer empresaId) {
        // Validar que no exista un precio para el mismo cliente y producto
        if (precioClienteJPARepository.existsByEmpresaIdAndTerceroIdAndProductoPresentacionId(
                empresaId, dto.getTerceroId(), dto.getProductoPresentacionId())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, 
                    "Ya existe un precio especial para este cliente y producto");
        }

        // Validar tercero
        TerceroEntity tercero = terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));

        // Validar producto presentación
        ProductoPresentacionEntity presentacion = productoPresentacionJPARepository
                .findByIdAndProductoEmpresaId(dto.getProductoPresentacionId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación de producto no encontrada"));

        // Obtener empresa
        EmpresaEntity empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Empresa no encontrada"));

        // Crear entidad
        PrecioClienteEntity entity = mapper.toEntity(dto);
        entity.setEmpresa(empresa);
        entity.setTercero(tercero);
        entity.setProductoPresentacion(presentacion);

        // Parsear fechas si existen
        if (dto.getFechaInicio() != null && !dto.getFechaInicio().isEmpty()) {
            entity.setFechaInicio(LocalDateTime.parse(dto.getFechaInicio()));
        }
        if (dto.getFechaFin() != null && !dto.getFechaFin().isEmpty()) {
            entity.setFechaFin(LocalDateTime.parse(dto.getFechaFin()));
        }

        return mapper.toDto(precioClienteJPARepository.save(entity));
    }

    @Override
    @Transactional
    public PrecioClienteDto actualizarPrecioCliente(Long id, UpdatePrecioClienteDto dto, Integer empresaId) {
        PrecioClienteEntity entity = precioClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio especial no encontrado"));

        // Validar duplicado si cambió el producto
        if (dto.getProductoPresentacionId() != null && 
                !dto.getProductoPresentacionId().equals(entity.getProductoPresentacion().getId())) {
            if (precioClienteJPARepository.existsByEmpresaIdAndTerceroIdAndProductoPresentacionIdAndIdNot(
                    empresaId, entity.getTercero().getId(), dto.getProductoPresentacionId(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, 
                        "Ya existe un precio especial para este cliente y producto");
            }
            
            ProductoPresentacionEntity presentacion = productoPresentacionJPARepository
                    .findByIdAndProductoEmpresaId(dto.getProductoPresentacionId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación de producto no encontrada"));
            entity.setProductoPresentacion(presentacion);
        }

        // Parsear fechas si existen
        if (dto.getFechaInicio() != null) {
            entity.setFechaInicio(LocalDateTime.parse(dto.getFechaInicio()));
        }
        if (dto.getFechaFin() != null) {
            entity.setFechaFin(LocalDateTime.parse(dto.getFechaFin()));
        }

        mapper.updateEntityFromDto(dto, entity);
        return mapper.toDto(precioClienteJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminarPrecioCliente(Long id, Integer empresaId) {
        PrecioClienteEntity entity = precioClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio especial no encontrado"));
        precioClienteJPARepository.deleteById(id);
    }

    // ========== DESCUENTO CLIENTE ==========

    @Override
    public PageImpl<DescuentoClienteDto> listarDescuentosCliente(PageableDto<Object> pageable, Integer empresaId) {
        return descuentoClienteQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public DescuentoClienteDto obtenerDescuentoClientePorId(Long id, Integer empresaId) {
        DescuentoClienteEntity entity = descuentoClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Descuento no encontrado"));
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public DescuentoClienteDto crearDescuentoCliente(CreateDescuentoClienteDto dto, Integer empresaId) {
        // Validar que no exista un descuento para el mismo cliente y categoría
        if (descuentoClienteJPARepository.existsByEmpresaIdAndTerceroIdAndCategoriaId(
                empresaId, dto.getTerceroId(), dto.getCategoriaId())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, 
                    "Ya existe un descuento para este cliente y categoría");
        }

        // Validar tercero
        TerceroEntity tercero = terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));

        // Validar categoría
        CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));

        // Obtener empresa
        EmpresaEntity empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Empresa no encontrada"));

        // Crear entidad
        DescuentoClienteEntity entity = mapper.toEntity(dto);
        entity.setEmpresa(empresa);
        entity.setTercero(tercero);
        entity.setCategoria(categoria);

        // Parsear fechas si existen
        if (dto.getFechaInicio() != null && !dto.getFechaInicio().isEmpty()) {
            entity.setFechaInicio(LocalDateTime.parse(dto.getFechaInicio()));
        }
        if (dto.getFechaFin() != null && !dto.getFechaFin().isEmpty()) {
            entity.setFechaFin(LocalDateTime.parse(dto.getFechaFin()));
        }

        return mapper.toDto(descuentoClienteJPARepository.save(entity));
    }

    @Override
    @Transactional
    public DescuentoClienteDto actualizarDescuentoCliente(Long id, UpdateDescuentoClienteDto dto, Integer empresaId) {
        DescuentoClienteEntity entity = descuentoClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Descuento no encontrado"));

        // Validar duplicado si cambió la categoría
        if (dto.getCategoriaId() != null && 
                !dto.getCategoriaId().equals(entity.getCategoria().getId())) {
            if (descuentoClienteJPARepository.existsByEmpresaIdAndTerceroIdAndCategoriaIdAndIdNot(
                    empresaId, entity.getTercero().getId(), dto.getCategoriaId(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, 
                        "Ya existe un descuento para este cliente y categoría");
            }
            
            CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            entity.setCategoria(categoria);
        }

        // Parsear fechas si existen
        if (dto.getFechaInicio() != null) {
            entity.setFechaInicio(LocalDateTime.parse(dto.getFechaInicio()));
        }
        if (dto.getFechaFin() != null) {
            entity.setFechaFin(LocalDateTime.parse(dto.getFechaFin()));
        }

        mapper.updateEntityFromDto(dto, entity);
        return mapper.toDto(descuentoClienteJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminarDescuentoCliente(Long id, Integer empresaId) {
        DescuentoClienteEntity entity = descuentoClienteJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Descuento no encontrado"));
        descuentoClienteJPARepository.deleteById(id);
    }

    // ========== PRECIO VOLUMEN ==========

    @Override
    public PageImpl<PrecioVolumenTableDto> listarPreciosVolumen(PageableDto<Object> pageable, Integer empresaId) {
        return precioVolumenQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public PrecioVolumenDto obtenerPrecioVolumenPorId(Long id, Integer empresaId) {
        PrecioVolumenEntity entity = precioVolumenJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio por volumen no encontrado"));
        return mapper.toDto(entity);
    }

    @Override
    public List<PrecioVolumenTableDto> listarPreciosVolumenPorProducto(Integer empresaId, Long productoPresentacionId) {
        return precioVolumenQueryRepository.listarPorProducto(empresaId, productoPresentacionId);
    }

    @Override
    @Transactional
    public PrecioVolumenDto crearPrecioVolumen(CreatePrecioVolumenDto dto, Integer empresaId) {
        // Validar que no exista un precio por volumen para el mismo rango de cantidad
        if (precioVolumenJPARepository.existsByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaAndCantidadMaxima(
                empresaId, dto.getProductoPresentacionId(), dto.getCantidadMinima(), dto.getCantidadMaxima())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, 
                    "Ya existe un precio por volumen para este rango de cantidad");
        }

        // Validar producto presentación
        ProductoPresentacionEntity presentacion = productoPresentacionJPARepository
                .findByIdAndProductoEmpresaId(dto.getProductoPresentacionId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación de producto no encontrada"));

        // Obtener empresa
        EmpresaEntity empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Empresa no encontrada"));

        // Crear entidad
        PrecioVolumenEntity entity = mapper.toEntity(dto);
        entity.setEmpresa(empresa);
        entity.setProductoPresentacion(presentacion);

        return mapper.toDto(precioVolumenJPARepository.save(entity));
    }

    @Override
    @Transactional
    public PrecioVolumenDto actualizarPrecioVolumen(Long id, UpdatePrecioVolumenDto dto, Integer empresaId) {
        PrecioVolumenEntity entity = precioVolumenJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio por volumen no encontrado"));

        // Validar duplicado si cambió el rango
        if ((dto.getCantidadMinima() != null && !dto.getCantidadMinima().equals(entity.getCantidadMinima())) ||
                (dto.getCantidadMaxima() != null && !dto.getCantidadMaxima().equals(entity.getCantidadMaxima()))) {
            Integer newMin = dto.getCantidadMinima() != null ? dto.getCantidadMinima() : entity.getCantidadMinima();
            Integer newMax = dto.getCantidadMaxima() != null ? dto.getCantidadMaxima() : entity.getCantidadMaxima();
            
            if (precioVolumenJPARepository.existsByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaAndCantidadMaximaAndIdNot(
                    empresaId, entity.getProductoPresentacion().getId(), newMin, newMax, id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, 
                        "Ya existe un precio por volumen para este rango de cantidad");
            }
        }

        mapper.updateEntityFromDto(dto, entity);
        return mapper.toDto(precioVolumenJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminarPrecioVolumen(Long id, Integer empresaId) {
        PrecioVolumenEntity entity = precioVolumenJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio por volumen no encontrado"));
        precioVolumenJPARepository.deleteById(id);
    }

    // ========== CÁLCULO DE PRECIOS ==========

    @Override
    public BigDecimal calcularPrecio(Integer empresaId, Long terceroId, Long productoPresentacionId, 
            Integer cantidad, BigDecimal precioBase) {
        
        // Prioridad 1: Precio especial por cliente
        Optional<PrecioClienteEntity> precioCliente = precioClienteJPARepository
                .findByEmpresaIdAndTerceroIdAndProductoPresentacionIdAndActivoTrue(
                        empresaId, terceroId, productoPresentacionId);
        
        if (precioCliente.isPresent()) {
            PrecioClienteEntity pc = precioCliente.get();
            // Verificar vigencia de fecha
            LocalDateTime now = LocalDateTime.now();
            if (pc.getFechaInicio() != null && pc.getFechaInicio().isAfter(now)) {
                // No ha iniciado, continuar a siguiente prioridad
            } else if (pc.getFechaFin() != null && pc.getFechaFin().isBefore(now)) {
                // Ya venció, continuar a siguiente prioridad
            } else {
                // Precio especial vigente
                return pc.getPrecioEspecial();
            }
        }

        // Prioridad 2: Descuento por cliente (se aplica sobre el precio base)
        // Primero necesitamos obtener la categoría del producto para buscar descuentos
        // Como no tenemos acceso directo a la categoría del producto desde aquí,
        // retornamos el precio especial o continuamos
        // (Esta lógica se implementaría con acceso a producto entity)
        
        // Por ahora, retornamos el precio especial si existe, o continuamos
        if (precioCliente.isPresent()) {
            return precioCliente.get().getPrecioEspecial();
        }

        // Prioridad 3: Precio por volumen
        Optional<PrecioVolumenEntity> precioVolumen = precioVolumenJPARepository
                .findByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaLessThanEqualAndCantidadMaximaGreaterThanEqualAndActivoTrue(
                        empresaId, productoPresentacionId, cantidad, cantidad);
        
        if (precioVolumen.isPresent()) {
            return precioVolumen.get().getPrecioUnitario();
        }

        // Prioridad 4: Precio base
        return precioBase;
    }
}
