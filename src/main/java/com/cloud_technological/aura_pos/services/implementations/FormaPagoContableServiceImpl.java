package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.FormaPagoContableDto;
import com.cloud_technological.aura_pos.entity.FormaPagoContableEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.FormaPagoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.FormaPagoContableService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FormaPagoContableServiceImpl implements FormaPagoContableService {

    private final FormaPagoContableJPARepository repo;
    private final PlanCuentaJPARepository planRepo;
    private final CuentaPorDefectoResolver porDefecto;

    @Override
    public List<FormaPagoContableDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .map(e -> toDto(empresaId, e))
                .toList();
    }

    @Override
    @Transactional
    public FormaPagoContableDto crear(Integer empresaId, FormaPagoContableDto dto) {
        String codigo = normalizarCodigo(dto.getCodigo());
        // CREDITO no es una forma de pago: la venta lo lee como "queda debiendo".
        if (codigo == null || codigo.isBlank() || "CREDITO".equals(codigo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escriba un código para la forma de pago (CREDITO está reservado para las ventas a crédito)");
        }
        if (repo.findByEmpresaIdAndCodigo(empresaId, codigo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una forma de pago con el código " + codigo);
        }
        validarCuenta(empresaId, dto.getCuentaContableId());

        FormaPagoContableEntity e = repo.save(FormaPagoContableEntity.builder()
                .empresaId(empresaId)
                .codigo(codigo)
                .nombre(dto.getNombre())
                .cuentaContableId(dto.getCuentaContableId())
                .requiereCuentaBancaria(Boolean.TRUE.equals(dto.getRequiereCuentaBancaria()))
                .activo(dto.getActivo() == null || dto.getActivo())
                .recargoPorcentaje(validarRecargo(dto.getRecargoPorcentaje()))
                .build());
        return toDto(empresaId, e);
    }

    @Override
    @Transactional
    public FormaPagoContableDto actualizar(Integer empresaId, Long id, FormaPagoContableDto dto) {
        FormaPagoContableEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Forma de pago no encontrada"));
        validarCuenta(empresaId, dto.getCuentaContableId());

        // El código no se edita: los documentos históricos lo referencian.
        e.setNombre(dto.getNombre());
        e.setCuentaContableId(dto.getCuentaContableId());
        if (dto.getRequiereCuentaBancaria() != null) {
            e.setRequiereCuentaBancaria(dto.getRequiereCuentaBancaria());
        }
        if (dto.getRecargoPorcentaje() != null) {
            e.setRecargoPorcentaje(validarRecargo(dto.getRecargoPorcentaje()));
        }
        if (dto.getActivo() != null) {
            e.setActivo(dto.getActivo());
        }
        return toDto(empresaId, repo.save(e));
    }

    @Override
    @Transactional
    public int copiarCuenta(Integer empresaId, Long origenId, List<Long> destinoIds) {
        FormaPagoContableEntity origen = repo.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Forma de pago no encontrada"));
        if (origen.getCuentaContableId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La forma de pago " + origen.getNombre() + " no tiene cuenta: no hay nada que copiar");
        }
        int copiadas = 0;
        for (Long id : destinoIds != null ? destinoIds : List.<Long>of()) {
            if (id == null || id.equals(origenId)) continue;
            FormaPagoContableEntity destino = repo.findByIdAndEmpresaId(id, empresaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Forma de pago destino no encontrada"));
            destino.setCuentaContableId(origen.getCuentaContableId());
            destino.setRequiereCuentaBancaria(origen.getRequiereCuentaBancaria());
            repo.save(destino);
            copiadas++;
        }
        return copiadas;
    }

    @Override
    public Long cuentaPara(Integer empresaId, String codigoMetodoPago) {
        if (codigoMetodoPago == null || codigoMetodoPago.isBlank()) {
            return null;
        }
        return repo.findByEmpresaIdAndCodigo(empresaId, normalizarCodigo(codigoMetodoPago))
                .filter(f -> Boolean.TRUE.equals(f.getActivo()))
                .map(FormaPagoContableEntity::getCuentaContableId)
                .filter(cuentaId -> planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                        .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                        .isPresent())
                .orElse(null);
    }

    @Override
    @Transactional
    public void seedDefaults(Integer empresaId) {
        seedUna(empresaId, "EFECTIVO", "Efectivo", "110505", false);
        seedUna(empresaId, "TRANSFERENCIA", "Transferencia bancaria", "1110", true);
        seedUna(empresaId, "TARJETA", "Tarjeta débito/crédito", "1110", false);
        seedUna(empresaId, "NEQUI", "Nequi", "1110", false);
        seedUna(empresaId, "DAVIPLATA", "Daviplata", "1110", false);
    }

    private void seedUna(Integer empresaId, String codigo, String nombre,
            String codigoCuenta, boolean requiereBanco) {
        if (repo.findByEmpresaIdAndCodigo(empresaId, codigo).isPresent()) {
            return;
        }
        Long cuentaId = porDefecto.idCuenta(empresaId, codigoCuenta);
        repo.save(FormaPagoContableEntity.builder()
                .empresaId(empresaId)
                .codigo(codigo)
                .nombre(nombre)
                .cuentaContableId(cuentaId)
                .requiereCuentaBancaria(requiereBanco)
                .activo(true)
                .build());
    }

    /**
     * Guardarraíl (ADR-006): la cuenta de una forma de pago solo puede ser
     * del disponible (11xx), auxiliar y activa. Nullable: sin cuenta, el
     * motor cae al fallback CAJA/BANCOS.
     */
    private void validarCuenta(Integer empresaId, Long cuentaContableId) {
        if (cuentaContableId == null) {
            return;
        }
        PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cuentaContableId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cuenta contable asignada no existe."));
        if (!Boolean.TRUE.equals(cuenta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta contable asignada está inactiva.");
        }
        if (!Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + cuenta.getCodigo() + " no es de movimiento (auxiliar).");
        }
        // 11xx: la plata entra ya (caja, banco, billetera). 13xx: la paga
        // después un tercero que financia al cliente (ADDI, Sistecrédito,
        // datáfono con abono a días): queda por cobrarle a él.
        if (cuenta.getCodigo() == null
                || !(cuenta.getCodigo().startsWith("11") || cuenta.getCodigo().startsWith("13"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de una forma de pago debe ser del disponible (11xx) o de cartera (13xx), "
                            + "p.ej. 1105 Caja, 111005 Banco X o 130510 Por cobrar ADDI.");
        }
    }

    /** Recargo entre 0 y 50 %; vacío = sin recargo. */
    private java.math.BigDecimal validarRecargo(java.math.BigDecimal pct) {
        if (pct == null) return java.math.BigDecimal.ZERO;
        if (pct.signum() < 0 || pct.compareTo(java.math.BigDecimal.valueOf(50)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El recargo debe estar entre 0 % y 50 %");
        }
        return pct;
    }

    private String normalizarCodigo(String codigo) {
        return codigo == null ? null : codigo.trim().toUpperCase();
    }

    private FormaPagoContableDto toDto(Integer empresaId, FormaPagoContableEntity e) {
        String cuentaLabel = null;
        if (e.getCuentaContableId() != null) {
            cuentaLabel = planRepo.findByIdAndEmpresaId(e.getCuentaContableId(), empresaId)
                    .map(c -> c.getCodigo() + " - " + c.getNombre())
                    .orElse(null);
        }
        return FormaPagoContableDto.builder()
                .id(e.getId())
                .codigo(e.getCodigo())
                .nombre(e.getNombre())
                .cuentaContableId(e.getCuentaContableId())
                .cuentaContable(cuentaLabel)
                .requiereCuentaBancaria(e.getRequiereCuentaBancaria())
                .activo(e.getActivo())
                .recargoPorcentaje(e.getRecargoPorcentaje())
                .build();
    }
}
