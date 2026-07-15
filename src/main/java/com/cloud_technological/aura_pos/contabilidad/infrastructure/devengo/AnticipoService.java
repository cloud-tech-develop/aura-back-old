package com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.entity.AnticipoCruceEntity;
import com.cloud_technological.aura_pos.entity.AnticipoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AnticipoCruceJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AnticipoJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Anticipos de clientes y a proveedores (E6): registro (el asiento AN nace
 * tras el commit) y cruce contra facturas — el cruce abona la CxC/CxP SIN
 * pasar por caja y genera el asiento AC.
 */
@Service
@RequiredArgsConstructor
public class AnticipoService {

    private final AnticipoJPARepository anticipoRepo;
    private final AnticipoCruceJPARepository cruceRepo;
    private final CuentaCobrarJPARepository cuentaCobrarRepo;
    private final CuentaPagarJPARepository cuentaPagarRepo;
    private final ApplicationEventPublisher eventPublisher;

    public List<AnticipoEntity> listar(Integer empresaId, Long terceroId) {
        if (terceroId != null) {
            return anticipoRepo.findByEmpresaIdAndTerceroIdAndEstadoOrderByFechaAsc(
                    empresaId, terceroId, "ACTIVO");
        }
        return anticipoRepo.findByEmpresaIdOrderByFechaDescIdDesc(empresaId);
    }

    @Transactional
    public AnticipoEntity crear(Integer empresaId, Long usuarioId, AnticipoEntity dto) {
        if (dto.getMonto() == null || dto.getMonto().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto del anticipo debe ser mayor que cero.");
        }
        if (!"CLIENTE".equals(dto.getTipo()) && !"PROVEEDOR".equals(dto.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de anticipo inválido: use CLIENTE o PROVEEDOR.");
        }
        if (dto.getTerceroId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El anticipo requiere el tercero.");
        }
        AnticipoEntity anticipo = anticipoRepo.save(AnticipoEntity.builder()
                .empresaId(empresaId)
                .tipo(dto.getTipo())
                .terceroId(dto.getTerceroId())
                .monto(dto.getMonto())
                .saldo(dto.getMonto())
                .metodoPago(dto.getMetodoPago() != null ? dto.getMetodoPago() : "EFECTIVO")
                .cuentaBancariaId(dto.getCuentaBancariaId())
                .fecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now())
                .observaciones(dto.getObservaciones())
                .estado("ACTIVO")
                .usuarioId(usuarioId)
                .build());

        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                "ANTICIPO", anticipo.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));
        return anticipo;
    }

    /**
     * Aplica saldo del anticipo a una factura (CxC si es de cliente, CxP si
     * es a proveedor). Todo o nada: transaccional.
     */
    @Transactional
    public AnticipoCruceEntity cruzar(Integer empresaId, Long usuarioId, Long anticipoId,
            Long cuentaCobrarId, Long cuentaPagarId, BigDecimal monto) {
        AnticipoEntity anticipo = anticipoRepo.findByIdAndEmpresaId(anticipoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Anticipo no encontrado"));
        if (!"ACTIVO".equals(anticipo.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El anticipo #" + anticipoId + " no está activo.");
        }
        if (monto == null || monto.signum() <= 0
                || monto.compareTo(anticipo.getSaldo()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto a cruzar debe ser positivo y no superar el saldo del anticipo ("
                            + anticipo.getSaldo() + ").");
        }

        AnticipoCruceEntity cruce = AnticipoCruceEntity.builder()
                .empresaId(empresaId)
                .anticipoId(anticipoId)
                .monto(monto)
                .fecha(LocalDate.now())
                .usuarioId(usuarioId)
                .build();

        if ("CLIENTE".equals(anticipo.getTipo())) {
            var cxc = cuentaCobrarRepo.findByIdAndEmpresaId(exigir(cuentaCobrarId,
                            "cuentaCobrarId es obligatorio para cruzar un anticipo de cliente"), empresaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Cuenta por cobrar no encontrada"));
            validarTercero(anticipo.getTerceroId(),
                    cxc.getTercero() != null ? cxc.getTercero().getId() : null);
            if (monto.compareTo(cxc.getSaldoPendiente()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El monto supera el saldo pendiente de la factura ("
                                + cxc.getSaldoPendiente() + ").");
            }
            cxc.setTotalAbonado(cxc.getTotalAbonado().add(monto));
            cxc.setSaldoPendiente(cxc.getSaldoPendiente().subtract(monto));
            if (cxc.getSaldoPendiente().signum() <= 0) {
                cxc.setSaldoPendiente(BigDecimal.ZERO);
                cxc.setEstado("pagada");
            }
            cuentaCobrarRepo.save(cxc);
            cruce.setCuentaCobrarId(cxc.getId());
        } else {
            var cxp = cuentaPagarRepo.findByIdAndEmpresaId(exigir(cuentaPagarId,
                            "cuentaPagarId es obligatorio para cruzar un anticipo a proveedor"), empresaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Cuenta por pagar no encontrada"));
            validarTercero(anticipo.getTerceroId(),
                    cxp.getTercero() != null ? cxp.getTercero().getId() : null);
            if (monto.compareTo(cxp.getSaldoPendiente()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El monto supera el saldo pendiente de la factura ("
                                + cxp.getSaldoPendiente() + ").");
            }
            cxp.setTotalAbonado(cxp.getTotalAbonado().add(monto));
            cxp.setSaldoPendiente(cxp.getSaldoPendiente().subtract(monto));
            if (cxp.getSaldoPendiente().signum() <= 0) {
                cxp.setSaldoPendiente(BigDecimal.ZERO);
                cxp.setEstado("pagada");
            }
            cuentaPagarRepo.save(cxp);
            cruce.setCuentaPagarId(cxp.getId());
        }

        anticipo.setSaldo(anticipo.getSaldo().subtract(monto));
        if (anticipo.getSaldo().signum() <= 0) {
            anticipo.setSaldo(BigDecimal.ZERO);
            anticipo.setEstado("APLICADO");
        }
        anticipoRepo.save(anticipo);
        AnticipoCruceEntity saved = cruceRepo.save(cruce);

        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                "ANTICIPO_CRUCE", saved.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));
        return saved;
    }

    private Long exigir(Long valor, String mensaje) {
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        return valor;
    }

    private void validarTercero(Long terceroAnticipo, Long terceroFactura) {
        if (terceroFactura != null && !terceroFactura.equals(terceroAnticipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La factura pertenece a otro tercero: el anticipo solo puede cruzarse "
                            + "con facturas del mismo tercero.");
        }
    }
}
