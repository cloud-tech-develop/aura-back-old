package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaClienteDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.CreateReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaTableDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.AnticipoEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.ReciboCajaAplicacionEntity;
import com.cloud_technological.aura_pos.entity.ReciboCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.cartera.ReciboCajaAplicacionJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.ReciboCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.ReciboCajaQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AnticipoJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.services.ReciboCajaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;

import lombok.RequiredArgsConstructor;

/**
 * Recibo de caja de cartera: un pago del cliente aplicado a varias facturas.
 *
 * <p>No reemplaza al abono: lo produce. Cada factura recibe un abonos_cobrar
 * con el mismo turno, cuenta y medio de pago que tendría un abono suelto, así
 * el arqueo lo cuenta, el asiento RC nace igual y los reportes de cartera
 * siguen cuadrando sin tocarlos. El recibo agrupa, numera (RCB) y anula junto.
 */
@Service
@RequiredArgsConstructor
public class ReciboCajaServiceImpl implements ReciboCajaService {

    /** Guard: no se registran documentos con fecha en un mes contable cerrado. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver periodoGuard;

    @org.springframework.beans.factory.annotation.Autowired
    private RetencionRecaudoService retencionRecaudo;

    private static final String PREFIJO = "RCB";

    private final ReciboCajaJPARepository reciboRepo;
    private final ReciboCajaAplicacionJPARepository aplicacionRepo;
    private final ReciboCajaQueryRepository queryRepo;
    private final CuentaCobrarJPARepository cuentaRepo;
    private final AbonoCobrarJPARepository abonoRepo;
    private final AnticipoJPARepository anticipoRepo;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final OrigenFondosService origenFondosService;
    private final PromesaPagoService promesaPagoService;
    private final AcuerdoPagoService acuerdoPagoService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReciboCajaDto crear(CreateReciboCajaDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getTerceroId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Seleccione el cliente que paga");
        terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        BigDecimal recibido = escala(dto.getValorRecibido());
        if (recibido.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El valor recibido debe ser mayor a 0");
        if (dto.getMetodoPago() == null || dto.getMetodoPago().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el medio de pago");
        if (MediosPago.esCredito(dto.getMetodoPago()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un pago de cartera no puede registrarse a crédito");

        // Las facturas se bloquean en orden de id: dos recibos cruzados no se esperan en círculo.
        List<CreateReciboCajaDto.Aplicacion> pedidas = new ArrayList<>(
                dto.getAplicaciones() != null ? dto.getAplicaciones() : List.of());
        pedidas.removeIf(a -> a.getMonto() == null || a.getMonto().signum() == 0);
        pedidas.sort(Comparator.comparing(CreateReciboCajaDto.Aplicacion::getCuentaCobrarId,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        Set<Long> vistas = new HashSet<>();
        List<CuentaCobrarEntity> cuentas = new ArrayList<>();
        List<List<com.cloud_technological.aura_pos.dto.cartera.RetencionRecaudoDto>> retencionesPorFactura =
                new ArrayList<>();
        BigDecimal aplicado = BigDecimal.ZERO;
        for (CreateReciboCajaDto.Aplicacion a : pedidas) {
            if (a.getCuentaCobrarId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Hay una aplicación sin factura");
            if (!vistas.add(a.getCuentaCobrarId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Una factura aparece dos veces en el recibo");
            BigDecimal monto = escala(a.getMonto());
            if (monto.signum() < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Los valores a aplicar no pueden ser negativos");
            CuentaCobrarEntity cuenta = cuentaRepo.bloquear(a.getCuentaCobrarId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                            "Cuenta por cobrar #" + a.getCuentaCobrarId() + " no encontrada"));
            if (cuenta.getDeletedAt() != null || "anulada".equals(cuenta.getEstado())
                    || "pagada".equals(cuenta.getEstado()) || cuenta.getSaldoPendiente().signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta " + cuenta.getNumeroCuenta() + " ya no tiene saldo por cobrar");
            if (cuenta.getTercero() == null || !dto.getTerceroId().equals(cuenta.getTercero().getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta " + cuenta.getNumeroCuenta() + " es de otro cliente");
            // Lo retenido por el cliente se suma a lo pagado para bajar la factura.
            var retenciones = retencionRecaudo.validar(a.getRetenciones());
            BigDecimal retenido = RetencionRecaudoService.total(retenciones);
            if (retenido.signum() > 0 && monto.signum() == 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "A la cuenta " + cuenta.getNumeroCuenta() + " se le registran retenciones sin "
                                + "pago: registre el valor pagado junto con lo retenido");
            if (monto.add(retenido).compareTo(cuenta.getSaldoPendiente()) > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "A la cuenta " + cuenta.getNumeroCuenta() + " se le aplica más de su saldo ("
                                + cuenta.getSaldoPendiente().toPlainString() + ")"
                                + (retenido.signum() > 0 ? " contando las retenciones" : ""));
            a.setMonto(monto);
            cuentas.add(cuenta);
            retencionesPorFactura.add(retenciones);
            aplicado = aplicado.add(monto);
        }
        if (aplicado.compareTo(recibido) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Lo aplicado a facturas supera el valor recibido");
        BigDecimal sobrante = recibido.subtract(aplicado);

        boolean cajaOtroDia = Boolean.TRUE.equals(dto.getCajaOtroDia());
        OrigenFondosService.OrigenFondos origen = origenFondosService.resolver(empresaId,
                new OrigenFondosService.Solicitud(dto.getMetodoPago(), dto.getTurnoCajaId(), null,
                        dto.getCuentaContableId(), dto.getSucursalId(), "recibo de caja", cajaOtroDia));

        // El anticipo no entra al arqueo: en efectivo sobre una caja abierta el
        // cajero esperaría menos plata de la que tiene. Ahí se entrega el cambio.
        if (sobrante.signum() > 0 && origen.tipo() == OrigenFondosService.Tipo.CAJA)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "En efectivo con caja abierta no se puede dejar sobrante como anticipo ($"
                            + sobrante.toPlainString() + "): entregue el cambio y registre el valor exacto, "
                            + "o registre el anticipo por una cuenta.");

        UsuarioEntity usuario = usuarioRepo.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        LocalDateTime fechaPago = dto.getFechaPago() != null ? dto.getFechaPago() : LocalDateTime.now();
        periodoGuard.exigirAbierto(empresaId, fechaPago.toLocalDate());
        String metodo = MediosPago.normalizar(dto.getMetodoPago());

        int consecutivo = queryRepo.siguienteConsecutivo(empresaId);
        ReciboCajaEntity recibo = reciboRepo.save(ReciboCajaEntity.builder()
                .empresaId(empresaId)
                .terceroId(dto.getTerceroId())
                .numero(String.format("%s-%06d", PREFIJO, consecutivo))
                .consecutivo(consecutivo)
                .fechaPago(fechaPago)
                .valorRecibido(recibido)
                .valorAplicado(aplicado)
                .valorAnticipo(sobrante)
                .metodoPago(metodo)
                .referencia(recortar(dto.getReferencia(), 255))
                .cuentaContableId(dto.getCuentaContableId())
                .turnoCajaId(origen.turnoId())
                .cajaOtroDia(cajaOtroDia)
                .observaciones(recortar(dto.getObservaciones(), 500))
                .usuarioId(usuario.getId())
                .build());

        String referenciaAbono = recortar(recibo.getNumero()
                + (dto.getReferencia() != null && !dto.getReferencia().isBlank() ? " · " + dto.getReferencia() : ""), 255);
        List<Long> abonosCreados = new ArrayList<>();
        for (int i = 0; i < cuentas.size(); i++) {
            CuentaCobrarEntity cuenta = cuentas.get(i);
            BigDecimal monto = pedidas.get(i).getMonto();
            if (monto.signum() == 0) continue;
            BigDecimal saldoAnterior = cuenta.getSaldoPendiente();

            AbonoCobrarEntity abono = abonoRepo.save(AbonoCobrarEntity.builder()
                    .cuentaCobrar(cuenta)
                    .usuario(usuario)
                    .turnoCaja(origen.turno())
                    .cuentaContableId(dto.getCuentaContableId())
                    .monto(monto)
                    .metodoPago(metodo)
                    .referencia(referenciaAbono)
                    .cajaOtroDia(cajaOtroDia)
                    .fechaPago(fechaPago)
                    .reciboCajaId(recibo.getId())
                    .build());
            abonosCreados.add(abono.getId());
            var retenciones = retencionesPorFactura.get(i);
            retencionRecaudo.registrar(abono, retenciones);
            BigDecimal retenido = RetencionRecaudoService.total(retenciones);
            BigDecimal bajaDeSaldo = monto.add(retenido);

            cuenta.setTotalAbonado(cuenta.getTotalAbonado().add(bajaDeSaldo));
            cuenta.setSaldoPendiente(saldoAnterior.subtract(bajaDeSaldo));
            if (cuenta.getSaldoPendiente().signum() <= 0) {
                cuenta.setSaldoPendiente(BigDecimal.ZERO);
                cuenta.setEstado("pagada");
            }
            cuentaRepo.save(cuenta);

            aplicacionRepo.save(ReciboCajaAplicacionEntity.builder()
                    .reciboCajaId(recibo.getId())
                    .cuentaCobrarId(cuenta.getId())
                    .abonoCobrarId(abono.getId())
                    .monto(monto)
                    .retenciones(retenido)
                    .saldoAnterior(saldoAnterior)
                    .build());
        }

        if (sobrante.signum() > 0) {
            AnticipoEntity anticipo = anticipoRepo.save(AnticipoEntity.builder()
                    .empresaId(empresaId)
                    .tipo("CLIENTE")
                    .terceroId(dto.getTerceroId())
                    .monto(sobrante)
                    .saldo(sobrante)
                    .metodoPago(metodo)
                    .cuentaContableId(origen.cuentaContableId())
                    .fecha(fechaPago.toLocalDate())
                    .observaciones("Sobrante del recibo " + recibo.getNumero())
                    .estado("ACTIVO")
                    .usuarioId(usuarioId)
                    .reciboCajaId(recibo.getId())
                    .build());
            recibo.setAnticipoId(anticipo.getId());
            reciboRepo.save(recibo);
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    "ANTICIPO", anticipo.getId(), empresaId, usuarioId.intValue()));
        }

        for (Long abonoId : abonosCreados) {
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    "ABONO_COBRAR", abonoId, empresaId, usuarioId.intValue()));
        }
        // La respuesta se lee por JDBC: sin flush no vería el anticipo recién enlazado.
        reciboRepo.flush();
        // El pago puede cumplir una promesa del cliente o cuotas de un acuerdo.
        promesaPagoService.evaluar(empresaId, dto.getTerceroId());
        acuerdoPagoService.evaluar(empresaId, dto.getTerceroId(), null);
        if (!abonosCreados.isEmpty())
            eventPublisher.publishEvent(new com.cloud_technological.aura_pos.event.CreditoMovimientoEvent(
                    dto.getTerceroId(), empresaId, "AL_PAGAR"));
        return queryRepo.obtener(recibo.getId(), empresaId);
    }

    @Override
    public ReciboCajaDto obtener(Long id, Integer empresaId) {
        ReciboCajaDto dto = queryRepo.obtener(id, empresaId);
        if (dto == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Recibo de caja no encontrado");
        return dto;
    }

    @Override
    public PageImpl<ReciboCajaTableDto> listar(Integer empresaId, Long terceroId, String estado, String search,
            int page, int rows) {
        return queryRepo.listar(empresaId, terceroId, estado, search, Math.max(page, 0),
                Math.min(Math.max(rows, 1), 200));
    }

    @Override
    @Transactional
    public ReciboCajaDto anular(Long id, String motivo, Integer empresaId, Long usuarioId) {
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique por qué se anula el recibo");
        ReciboCajaEntity recibo = reciboRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Recibo de caja no encontrado"));
        if (!"ACTIVO".equals(recibo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El recibo ya está anulado");

        // Anticipo: solo si nadie lo ha cruzado todavía.
        if (recibo.getAnticipoId() != null) {
            AnticipoEntity anticipo = anticipoRepo.findByIdAndEmpresaId(recibo.getAnticipoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT, "El anticipo del recibo no existe"));
            if (anticipo.getSaldo().compareTo(anticipo.getMonto()) != 0 || !"ACTIVO".equals(anticipo.getEstado()))
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El sobrante de este recibo ya se cruzó contra otra factura. Reverse ese cruce antes de anular.");
            anticipo.setSaldo(BigDecimal.ZERO);
            anticipo.setEstado("ANULADO");
            anticipoRepo.save(anticipo);
            eventPublisher.publishEvent(new ContabilidadReversaEvent(
                    "ANTICIPO", anticipo.getId(), empresaId, usuarioId.intValue()));
        }

        List<ReciboCajaAplicacionEntity> aplicaciones = aplicacionRepo.findByReciboCajaIdOrderByIdAsc(recibo.getId());
        aplicaciones.sort(Comparator.comparing(ReciboCajaAplicacionEntity::getCuentaCobrarId));
        for (ReciboCajaAplicacionEntity ap : aplicaciones) {
            AbonoCobrarEntity abono = ap.getAbonoCobrarId() != null
                    ? abonoRepo.findById(ap.getAbonoCobrarId()).orElse(null)
                    : null;
            if (abono == null) continue;
            // El efectivo ya contado en un arqueo cerrado no puede desaparecer de él.
            if (abono.getTurnoCaja() != null && !"ABIERTA".equals(abono.getTurnoCaja().getEstado())
                    && MediosPago.esEfectivo(abono.getMetodoPago()))
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El efectivo de este recibo entró a un turno de caja que ya cerró; no se puede anular.");

            CuentaCobrarEntity cuenta = cuentaRepo.bloquear(ap.getCuentaCobrarId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT, "Cuenta por cobrar no encontrada"));
            // El pago y sus retenciones vuelven juntos al saldo: el asiento del
            // abono principal incluía las dos cosas.
            BigDecimal devuelto = abono.getMonto().add(retencionRecaudo.eliminarDe(abono.getId()));
            cuenta.setTotalAbonado(cuenta.getTotalAbonado().subtract(devuelto));
            cuenta.setSaldoPendiente(cuenta.getSaldoPendiente().add(devuelto));
            if (cuenta.getDeletedAt() == null && !"anulada".equals(cuenta.getEstado()))
                cuenta.setEstado("activa");
            cuentaRepo.save(cuenta);
            abonoRepo.delete(abono);
            eventPublisher.publishEvent(new ContabilidadReversaEvent(
                    "ABONO_COBRAR", abono.getId(), empresaId, usuarioId.intValue()));
        }

        recibo.setEstado("ANULADO");
        recibo.setMotivoAnulacion(recortar(motivo.trim(), 300));
        recibo.setAnuladoPor(usuarioId.intValue());
        recibo.setAnuladoAt(LocalDateTime.now());
        reciboRepo.saveAndFlush(recibo);
        abonoRepo.flush();
        // Sin estos abonos una promesa que se daba por cumplida puede volver a pendiente.
        promesaPagoService.evaluar(empresaId, recibo.getTerceroId());
        acuerdoPagoService.evaluar(empresaId, recibo.getTerceroId(), null);
        return queryRepo.obtener(recibo.getId(), empresaId);
    }

    @Override
    @Transactional
    public FichaClienteCarteraDto ficha(Long terceroId, Integer empresaId) {
        promesaPagoService.evaluar(empresaId, terceroId);
        acuerdoPagoService.evaluar(empresaId, terceroId, null);
        FichaClienteDto cliente = queryRepo.cliente(terceroId, empresaId);
        if (cliente == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        FichaClienteCarteraDto ficha = new FichaClienteCarteraDto();
        ficha.setCliente(cliente);
        var resumen = queryRepo.resumen(terceroId, empresaId);
        if (cliente.getCupoCredito() != null)
            resumen.setCupoDisponible(cliente.getCupoCredito().subtract(resumen.getSaldoTotal()).max(BigDecimal.ZERO));
        ficha.setResumen(resumen);
        ficha.setFacturas(queryRepo.facturasAbiertas(terceroId, empresaId));
        ficha.setPagos(queryRepo.pagos(terceroId, empresaId, 100));
        ficha.setRecibos(queryRepo.recibos(terceroId, empresaId, 50));
        ficha.setGestiones(queryRepo.gestiones(terceroId, empresaId, 50));
        ficha.setAnticipos(queryRepo.anticiposActivos(terceroId, empresaId));
        ficha.setHistorialCredito(queryRepo.historialCredito(terceroId, empresaId, 50));
        ficha.setAcuerdos(acuerdoPagoService.delCliente(terceroId, empresaId));
        return ficha;
    }

    private static BigDecimal escala(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
