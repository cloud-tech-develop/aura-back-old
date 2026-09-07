package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
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

        // (0) "Ya se movió de la caja, otro día". Va primero porque es una
        // afirmación sobre un hecho pasado, no una elección entre cuentas: la
        // plata ya salió (o ya entró) y el arqueo de aquel día ya cuadró contra
        // el conteo físico. Contablemente afecta CAJA; operativamente no mueve
        // nada.
        //
        // El turno se descarta a propósito, aunque el documento lo haya
        // declarado. En compra y gasto bastaba con no emitir el movimiento de
        // caja, pero un abono de cartera se cuenta en el arqueo por su propio
        // turno_caja_id: dejarlo puesto le metería a ese turno un ingreso o un
        // egreso que su conteo físico ya reflejaba. Devolverlo en null vuelve
        // la regla estructural en vez de dejarla en manos de cada llamador.
        if (solicitud.cajaOtroDia()) {
            return new OrigenFondos(Tipo.CAJA_OTRO_DIA,
                    resolucionCuentaPago.resolver(empresaId, MediosPago.EFECTIVO, null),
                    null);
        }

        // (1) Cuenta contable elegida a mano: el caso del administrador que
        // paga desde una cuenta que no es ni la caja del punto ni un banco —
        // típicamente la CAJA MENOR, o una cuenta puente de fondos entregados
        // a alguien que todavía no los legaliza.
        //
        // Manda sobre todo lo demás **aunque haya un turno de caja abierto**:
        // que exista una caja abierta no significa que la plata haya salido de
        // ella. Quien registra el documento es quien sabe de dónde salió.
        if (solicitud.cuentaContableId() != null) {
            Long cuenta = validarMedioPago(solicitud.cuentaContableId(), empresaId, doc);
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
            boolean inferido = false;
            if (turno == null) {
                turno = turnoAbiertoDeLaSucursal(solicitud, doc);
                inferido = true;
            }
            return new OrigenFondos(Tipo.CAJA,
                    resolucionCuentaPago.resolver(empresaId, solicitud.metodoPago(), null),
                    turno, inferido);
        }

        // (4) Resto de medios (tarjeta, transferencia sin cuenta elegida…):
        // la cuenta sale de la forma de pago parametrizada o del fallback.
        return new OrigenFondos(Tipo.CUENTA_CONTABLE,
                resolucionCuentaPago.resolver(empresaId, solicitud.metodoPago(), null),
                turno);
    }

    /**
     * La cuenta elegida a mano tiene que representar dinero disponible.
     *
     * <p>Antes bastaba con que existiera y estuviera activa, así que se podía
     * pagar un gasto acreditando una cuenta de ingresos y el asiento quedaba
     * sin sentido, sin que nadie lo notara hasta cerrar el mes. El flag
     * es_medio_pago lo administra el contador desde el plan de cuentas: es lo
     * que marca cuando crea su CAJA MENOR bajo la 1105.
     */
    private Long validarMedioPago(Long cuentaId, Integer empresaId, String doc) {
        PlanCuentaEntity cuenta = planCuentaRepository
                .findByIdAndEmpresaId(cuentaId, empresaId)
                .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta contable elegida para el " + doc
                                + " no existe o está inactiva"));

        if (!Boolean.TRUE.equals(cuenta.getEsMedioPago())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + cuenta.getCodigo() + " — " + cuenta.getNombre()
                            + " no está habilitada como medio de pago. Habilítela desde el "
                            + "plan de cuentas o elija otra cuenta de origen para el " + doc);
        }
        // Una cuenta de agrupación no recibe movimientos: dejarla pasar produce
        // un asiento que el validador contable rechaza más adelante, lejos de
        // donde se cometió el error.
        if (!Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + cuenta.getCodigo() + " — " + cuenta.getNombre()
                            + " es una cuenta de agrupación y no admite movimientos. "
                            + "Elija una subcuenta auxiliar para el " + doc);
        }
        return cuenta.getId();
    }

    /**
     * Deduce el turno cuando el documento no lo declaró.
     *
     * <p>Solo es legítimo si hay <b>exactamente uno</b> abierto en la sucursal.
     * Antes se tomaba el más antiguo de los que hubiera, que con dos cajas
     * abiertas equivale a adivinar de cuál cajón salió la plata — y el
     * descuadre le caía a un cajero que no gastó nada. Con más de uno, el
     * documento tiene que decir cuál.
     */
    private TurnoCajaEntity turnoAbiertoDeLaSucursal(Solicitud solicitud, String doc) {
        if (solicitud.sucursalId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Debe indicar la sucursal para registrar el " + doc + " en efectivo, "
                            + "o elegir una cuenta bancaria o contable de origen");
        }
        List<TurnoCajaEntity> abiertos =
                turnoRepository.findByCajaSucursalIdAndEstado(solicitud.sucursalId(), ABIERTA);

        if (abiertos.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay una caja abierta en la sucursal para registrar el " + doc
                            + " en efectivo. Abra la caja o registre el pago contra una "
                            + "cuenta bancaria o una cuenta contable");
        }
        if (abiertos.size() > 1) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Hay " + abiertos.size() + " cajas abiertas en la sucursal. Indique de "
                            + "cuál sale el " + doc + ", o regístrelo contra una cuenta "
                            + "bancaria o contable");
        }
        return abiertos.get(0);
    }
}
