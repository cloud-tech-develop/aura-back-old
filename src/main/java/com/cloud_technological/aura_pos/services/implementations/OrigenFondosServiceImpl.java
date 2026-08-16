package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;

import lombok.RequiredArgsConstructor;

/**
 * Implementación del resolutor de origen de fondos.
 *
 * <p>El orden de evaluación va de lo más explícito a lo más inferido, para que
 * lo que el usuario eligió a mano nunca lo pise un fallback: cuenta contable →
 * cuenta bancaria → efectivo (caja) → forma de pago parametrizada.
 *
 * <p>La cuenta contable de cada vía se delega en {@link ResolucionCuentaPago},
 * que ya conoce la cadena banco → forma de pago → CAJA/BANCOS. Lo que agrega
 * este servicio es la parte operativa que faltaba: exigir y ubicar el turno de
 * caja cuando hay dinero físico de por medio.
 */
@Service
@RequiredArgsConstructor
public class OrigenFondosServiceImpl implements OrigenFondosService {

    private static final String ABIERTA = "ABIERTA";

    private final TurnoCajaJPARepository turnoRepository;
    private final PlanCuentaJPARepository planCuentaRepository;
    private final ResolucionCuentaPago resolucionCuentaPago;

    @Override
    public OrigenFondos resolver(Integer empresaId, Solicitud solicitud) {
        String doc = solicitud.documento() != null ? solicitud.documento() : "documento";

        // El turno declarado se valida siempre, aunque el pago no sea en
        // efectivo: sirve de trazabilidad de quién registró el documento.
        TurnoCajaEntity turno = null;
        if (solicitud.turnoCajaId() != null) {
            turno = turnoRepository
                    .findByIdAndCajaSucursalEmpresaId(solicitud.turnoCajaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Turno de caja no encontrado"));
            if (!ABIERTA.equals(turno.getEstado())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El turno de caja no está abierto");
            }
        }

        // (1) Cuenta contable elegida a mano: el caso del administrador que
        // paga desde una cuenta que no es ni la caja del punto ni un banco.
        if (solicitud.cuentaContableId() != null) {
            Long cuenta = planCuentaRepository
                    .findByIdAndEmpresaId(solicitud.cuentaContableId(), empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                    .map(c -> c.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "La cuenta contable elegida para el " + doc
                                    + " no existe o está inactiva"));
            return new OrigenFondos(Tipo.CUENTA_CONTABLE, cuenta, turno);
        }

        // (2) Cuenta bancaria: no toca caja física ni cierre.
        if (solicitud.cuentaBancariaId() != null) {
            return new OrigenFondos(Tipo.BANCO,
                    resolucionCuentaPago.resolver(empresaId, solicitud.metodoPago(),
                            solicitud.cuentaBancariaId()),
                    turno);
        }

        // (3) Efectivo: hay dinero físico, así que tiene que existir una caja
        // abierta donde quede registrado. Si el documento no declaró turno se
        // busca el de la sucursal — el dinero es de la caja, no del usuario.
        if (MediosPago.esEfectivo(solicitud.metodoPago())) {
            if (turno == null) {
                turno = turnoAbiertoDeLaSucursal(solicitud, doc);
            }
            return new OrigenFondos(Tipo.CAJA,
                    resolucionCuentaPago.resolver(empresaId, solicitud.metodoPago(), null),
                    turno);
        }

        // (4) Resto de medios (tarjeta, transferencia sin cuenta elegida…):
        // la cuenta sale de la forma de pago parametrizada o del fallback.
        return new OrigenFondos(Tipo.CUENTA_CONTABLE,
                resolucionCuentaPago.resolver(empresaId, solicitud.metodoPago(), null),
                turno);
    }

    private TurnoCajaEntity turnoAbiertoDeLaSucursal(Solicitud solicitud, String doc) {
        if (solicitud.sucursalId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Debe indicar la sucursal para registrar el " + doc + " en efectivo, "
                            + "o elegir una cuenta bancaria o contable de origen");
        }
        return turnoRepository
                .findFirstByCajaSucursalIdAndEstadoOrderByFechaAperturaAsc(
                        solicitud.sucursalId(), ABIERTA)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "No hay una caja abierta en la sucursal para registrar el " + doc
                                + " en efectivo. Abra la caja o registre el pago contra una "
                                + "cuenta bancaria o una cuenta contable"));
    }
}
