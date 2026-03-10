package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.facturacion.NotaContableDto;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.FacturaEntity;
import com.cloud_technological.aura_pos.entity.NotaContableEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.NotaContableMapper;
import com.cloud_technological.aura_pos.repositories.compras.CompraJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.notas_contables.NotaContableJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.NotaContableService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.NotaContableTipo;

@Service
public class NotaContableServiceImpl implements NotaContableService {

    private final NotaContableJPARepository notaContableRepository;
    private final FacturaJPARepository facturaRepository;
    private final CompraJPARepository compraRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final NotaContableMapper notaContableMapper;

    @Autowired
    public NotaContableServiceImpl(NotaContableJPARepository notaContableRepository,
            FacturaJPARepository facturaRepository,
            CompraJPARepository compraRepository,
            UsuarioJPARepository usuarioRepository,
            NotaContableMapper notaContableMapper) {
        this.notaContableRepository = notaContableRepository;
        this.facturaRepository = facturaRepository;
        this.compraRepository = compraRepository;
        this.usuarioRepository = usuarioRepository;
        this.notaContableMapper = notaContableMapper;
    }

    @Override
    @Transactional
    public NotaContableDto crear(NotaContableDto dto, Integer empresaId, Integer usuarioId) {
        // Validar tipo
        if (!NotaContableTipo.esValido(dto.getTipo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de nota inválido. Use 1 para CRÉDITO o 2 para DÉBITO");
        }

        // Validar valor
        if (dto.getValor() == null || dto.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El valor debe ser mayor a 0");
        }

        // Validar factura
        FacturaEntity factura = facturaRepository.findByIdAndEmpresaId(dto.getFacturaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));

        // Validar usuario
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Crear nota contable
        NotaContableEntity entity = new NotaContableEntity();
        entity.setFactura(factura);
        entity.setUsuario(usuario);
        entity.setValor(dto.getValor());
        entity.setBanco(dto.getBanco());
        entity.setTipo(dto.getTipo());
        entity.setNota(dto.getNota());
        entity.setMetodoPago(dto.getMetodoPago());
        entity.setCreatedAt(LocalDateTime.now());

        entity = notaContableRepository.save(entity);
        return notaContableMapper.toDto(entity);
    }

    @Override
    public List<NotaContableDto> obtenerPorFactura(Long facturaId, Integer empresaId) {
        // Validar que la factura existe
        facturaRepository.findByIdAndEmpresaId(facturaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));

        return notaContableRepository.findByFacturaIdOrderByCreatedAtDesc(facturaId)
                .stream()
                .map(notaContableMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotaContableDto generarNotaCreditoVuelto(Long facturaId, BigDecimal monto, Integer usuarioId, 
            String metodoPago, String descripcion) {
        
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(facturaId);
        dto.setValor(monto);
        dto.setTipo(NotaContableTipo.CREDITO);
        dto.setNota(descripcion != null ? descripcion : "Vuelto para el cliente");
        dto.setMetodoPago(metodoPago);
        
        // La empresa se obtendrá de la factura
        FacturaEntity factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
        
        return crear(dto, factura.getEmpresa().getId(), usuarioId);
    }

    @Override
    @Transactional
    public NotaContableDto generarNotaDebitoPago(Long facturaId, BigDecimal monto, Integer usuarioId, 
            String metodoPago, String descripcion) {
        
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(facturaId);
        dto.setValor(monto);
        dto.setTipo(NotaContableTipo.DEBITO);
        dto.setNota(descripcion != null ? descripcion : "Pago registrado");
        dto.setMetodoPago(metodoPago);
        
        // La empresa se obtendrá de la factura
        FacturaEntity factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
        
        return crear(dto, factura.getEmpresa().getId(), usuarioId);
    }

    @Override
    @Transactional
    public NotaContableDto generarNotaDebitoPagoCompra(Long compraId, BigDecimal monto, Integer usuarioId, 
            String metodoPago, String descripcion) {
        
        // La empresa se obtendrá de la compra
        CompraEntity compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
        
        // Validar usuario
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        
        // Crear nota contable de DÉBITO (salida de dinero en compras)
        NotaContableEntity entity = new NotaContableEntity();
        entity.setCompra(compra);
        entity.setUsuario(usuario);
        entity.setValor(monto);
        entity.setTipo(NotaContableTipo.DEBITO);
        entity.setNota(descripcion != null ? descripcion : "Pago de compra");
        entity.setMetodoPago(metodoPago);
        entity.setCreatedAt(LocalDateTime.now());
        
        entity = notaContableRepository.save(entity);
        return notaContableMapper.toDto(entity);
    }
}
