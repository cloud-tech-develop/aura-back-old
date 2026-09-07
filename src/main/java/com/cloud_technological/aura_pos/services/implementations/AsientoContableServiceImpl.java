package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateComprobanteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaProyeccionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.services.AsientoContableService;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.utils.AsientoBalanceValidator;
import com.cloud_technological.aura_pos.utils.MediosPago;

@Service
public class AsientoContableServiceImpl implements AsientoContableService {

    @Autowired
    private AsientoContableJPARepository repo;

    @Autowired
    private AsientoContableQueryRepository queryRepo;

    @Autowired
    private PeriodoContableJPARepository periodoRepo;

    @Autowired
    private TerceroJPARepository terceroRepo;

    @Autowired
    private com.cloud_technological.aura_pos.services.CuentaCobrarService cuentaCobrarService;

    @Autowired
    private com.cloud_technological.aura_pos.services.CuentaPagarService cuentaPagarService;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository empresaRepo;

    @Autowired
    private com.cloud_technological.aura_pos.services.OrigenFondosService origenFondosService;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository movimientoCajaRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository turnoCajaRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository usuarioRepo;

    @Autowired
    private com.cloud_technological.aura_pos.services.ComprobanteCajaService comprobanteCajaService;

    @Override
    public List<AsientoContableTableDto> listar(Integer empresaId, String desde, String hasta,
            String tipoOrigen, int page, int rows) {
        return queryRepo.paginar(empresaId, desde, hasta, tipoOrigen, page, rows);
    }

    @Override
    public AsientoContableTableDto obtenerConDetalles(Long id, Integer empresaId) {
        AsientoContableEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
        AsientoContableTableDto dto = toTableDto(entity);
        dto.setDetalles(queryRepo.obtenerDetalles(id));
        return dto;
    }

    @Override
    @Transactional
    public AsientoContableTableDto crear(Integer empresaId, Integer usuarioId, CreateAsientoDto dto) {
        BigDecimal totalDebito = dto.getDetalles().stream()
                .map(d -> d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredito = dto.getDetalles().stream()
                .map(d -> d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AsientoBalanceValidator.validarCuadre(totalDebito, totalCredito);

        // Validar período contable abierto
        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de registrar asientos."));

        // La cuenta dejó de ser @NotNull en el DTO porque el comprobante manual
        // deriva la de su contrapartida del origen de fondos. En el asiento
        // directo no hay nada que derivar: la cuenta la pone el usuario o no
        // hay línea.
        if (dto.getDetalles().stream().anyMatch(d -> d.getCuentaId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada línea del asiento debe tener una cuenta contable");
        }

        List<AsientoDetalleEntity> detalles = dto.getDetalles().stream()
                .map(d -> AsientoDetalleEntity.builder()
                        .cuentaId(d.getCuentaId())
                        .descripcion(d.getDescripcion())
                        .debito(d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO)
                        .credito(d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO)
                        .terceroId(d.getTerceroId())
                        .centroCostoId(d.getCentroCostoId())
                        .build())
                .collect(Collectors.toList());

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, "CD");

        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(dto.getFecha())
                .descripcion(dto.getDescripcion().trim())
                .tipoOrigen("MANUAL")
                .periodoContableId(periodo.getId())
                .numeroComprobante(comprobante)
                .totalDebito(totalDebito)
                .totalCredito(totalCredito)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .detalles(detalles)
                .build();

        // Relación bidireccional: el hijo debe apuntar al padre antes del save
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = repo.save(asiento);

        AsientoContableTableDto result = toTableDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional
    public AsientoContableTableDto crearComprobante(Integer empresaId, Integer usuarioId,
            CreateComprobanteDto dto) {
        BigDecimal totalDebito = dto.getDetalles().stream()
                .map(d -> d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredito = dto.getDetalles().stream()
                .map(d -> d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AsientoBalanceValidator.validarCuadre(totalDebito, totalCredito);

        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de registrar comprobantes."));

        String tipo = dto.getTipoComprobante().trim().toUpperCase();

        // ── De dónde sale (o entra) la plata ─────────────────────────────
        //
        // El CE y el RC mueven dinero real; el CD es una reclasificación entre
        // cuentas y no toca ningún cajón. Antes ninguno de los tres lo
        // declaraba: la contrapartida era una cuenta 11xx elegida a mano, y una
        // cuenta contable no distingue el cajón de la sucursal 2 de la cuenta
        // del banco. Resultado: el recaudo no caía en el cierre de ninguna caja
        // y el asiento podía acreditar CAJA por una transferencia.
        //
        // Se exige el método de pago en vez de inventar un default: resolver
        // con metodoPago null caería en el fallback de cuenta y volvería a
        // pisar en silencio lo que el usuario eligió.
        boolean mueveDinero = "CE".equals(tipo) || "RC".equals(tipo);
        OrigenFondosService.OrigenFondos origen = null;
        if (mueveDinero) {
            if (isBlank(dto.getMetodoPago())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Indique el método de pago del comprobante " + tipo
                                + ": de ahí depende en qué caja o cuenta queda registrado el dinero.");
            }
            origen = origenFondosService.resolver(empresaId,
                    new OrigenFondosService.Solicitud(
                            dto.getMetodoPago(),
                            dto.getTurnoCajaId(),
                            dto.getCuentaBancariaId(),
                            dto.getCuentaContableId(),
                            dto.getSucursalId(),
                            "comprobante " + tipo,
                            Boolean.TRUE.equals(dto.getCajaOtroDia())));
        }

        // Snapshot del beneficiario: si viene el tercero y faltan datos, se completan desde su ficha.
        String benefNombre = dto.getBeneficiarioNombre();
        String benefDireccion = dto.getBeneficiarioDireccion();
        String benefTelefono = dto.getBeneficiarioTelefono();
        if (dto.getBeneficiarioTerceroId() != null) {
            TerceroEntity t = terceroRepo.findByIdAndEmpresaId(dto.getBeneficiarioTerceroId(), empresaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Beneficiario (tercero) no encontrado"));
            if (isBlank(benefNombre)) benefNombre = nombreTercero(t);
            if (isBlank(benefDireccion)) benefDireccion = t.getDireccion();
            if (isBlank(benefTelefono)) benefTelefono = t.getTelefono();
        }

        // La cuenta de la contrapartida la pone el origen, no el usuario: es lo
        // que impide que un pago recibido por transferencia quede acreditando
        // la caja. El resto de líneas conserva la cuenta que se digitó.
        List<AsientoDetalleEntity> detalles = new java.util.ArrayList<>();
        BigDecimal montoContrapartida = BigDecimal.ZERO;
        for (CreateAsientoDetalleDto d : dto.getDetalles()) {
            boolean esContrapartida = CreateAsientoDetalleDto.ORIGEN_BANCO.equalsIgnoreCase(d.getOrigen());
            Long cuentaId = esContrapartida && origen != null ? origen.cuentaContableId() : d.getCuentaId();
            if (cuentaId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada línea del comprobante debe tener una cuenta contable");
            }
            BigDecimal debito = d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO;
            BigDecimal credito = d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO;
            if (esContrapartida) {
                montoContrapartida = montoContrapartida.add(debito).add(credito);
            }
            detalles.add(AsientoDetalleEntity.builder()
                    .cuentaId(cuentaId)
                    .descripcion(d.getDescripcion())
                    .debito(debito)
                    .credito(credito)
                    .terceroId(d.getTerceroId())
                    .centroCostoId(d.getCentroCostoId())
                    .build());
        }
        // Comprobante que no marcó su contrapartida (front viejo): el dinero
        // movido es el total del asiento. Basta para no perder el movimiento de
        // caja; marcarla es lo que lo vuelve exacto en asientos mixtos.
        if (montoContrapartida.signum() == 0) {
            montoContrapartida = totalDebito;
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, tipo);

        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(dto.getFecha())
                .descripcion(dto.getConcepto().trim())
                .tipoOrigen("MANUAL")
                .tipoComprobante(tipo)
                .beneficiarioTerceroId(dto.getBeneficiarioTerceroId())
                .beneficiarioNombre(benefNombre)
                .beneficiarioDireccion(benefDireccion)
                .beneficiarioTelefono(benefTelefono)
                .ciudad(dto.getCiudad())
                .fechaVencimiento(dto.getFechaVencimiento())
                .periodoContableId(periodo.getId())
                .numeroComprobante(comprobante)
                .totalDebito(totalDebito)
                .totalCredito(totalCredito)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                // Se guarda lo que el comprobante declaró, no lo que se dedujo
                // después: es lo que permite reconstruir meses más tarde por qué
                // este CE cayó en una caja y no en otra.
                .turnoCajaId(origen != null ? origen.turnoId() : null)
                .metodoPago(origen != null ? MediosPago.normalizar(dto.getMetodoPago()) : null)
                .cuentaBancariaId(mueveDinero ? dto.getCuentaBancariaId() : null)
                .cajaOtroDia(origen != null
                        && origen.tipo() == OrigenFondosService.Tipo.CAJA_OTRO_DIA)
                .detalles(detalles)
                .build();

        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = repo.save(asiento);

        // ── Cruce de cartera (aplicación del pago) en la misma transacción ──
        //
        // El origen viaja con el cruce: es lo que ata el abono a un turno y le
        // pone su método de pago real, y por tanto lo que lo hace aparecer en el
        // cierre de caja. Antes el abono nacía sin turno y el comprobante movía
        // efectivo sin dejar rastro en el arqueo de nadie.
        BigDecimal totalAplicado = BigDecimal.ZERO;
        String referencia = "Comprobante " + comprobante;
        if (dto.getAplicaciones() != null) {
            for (var ap : dto.getAplicaciones()) {
                if (ap == null || ap.getCuentaId() == null
                        || ap.getMonto() == null || ap.getMonto().signum() <= 0) continue;
                if ("CXP".equalsIgnoreCase(ap.getTipo())) {
                    cuentaPagarService.aplicarCruce(ap.getCuentaId(), ap.getMonto(), empresaId,
                            usuarioId, referencia, origen, dto.getMetodoPago());
                } else if ("CXC".equalsIgnoreCase(ap.getTipo())) {
                    cuentaCobrarService.aplicarCruce(ap.getCuentaId(), ap.getMonto(), empresaId,
                            usuarioId, referencia, origen, dto.getMetodoPago());
                }
                totalAplicado = totalAplicado.add(ap.getMonto());
            }
        }

        registrarMovimientoDeCaja(saved, origen, tipo, montoContrapartida.subtract(totalAplicado),
                dto, usuarioId);
        emitirComprobanteDeCaja(saved, origen, tipo, montoContrapartida, dto, usuarioId);

        AsientoContableTableDto result = toTableDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    /**
     * El efectivo de un comprobante sale (o entra) de una caja y tiene que
     * verse en su cierre.
     *
     * <p>Solo se registra la parte que <b>no</b> quedó ya representada por un
     * abono de cartera: esos abonos entran al arqueo por su propio turno, así
     * que sumarlos otra vez aquí duplicaría la plata. Lo que queda es el CE que
     * paga un servicio suelto o el RC que recibe algo que no es cartera — antes
     * esos no tocaban caja por ningún lado.
     *
     * @param monto la contrapartida menos lo aplicado a cartera
     */
    private void registrarMovimientoDeCaja(AsientoContableEntity asiento,
            OrigenFondosService.OrigenFondos origen, String tipo, BigDecimal monto,
            CreateComprobanteDto dto, Integer usuarioId) {
        if (origen == null || !origen.generaMovimientoCaja()
                || monto == null || monto.signum() <= 0) {
            return;
        }
        MovimientoCajaEntity movimiento = MovimientoCajaEntity.builder()
                .turnoCaja(origen.turno())
                .usuario(usuarioId != null ? usuarioRepo.findById(usuarioId).orElse(null) : null)
                .tipo("CE".equals(tipo) ? "EGRESO" : "INGRESO")
                .concepto("Comprobante " + asiento.getNumeroComprobante()
                        + " — " + asiento.getDescripcion())
                .monto(monto)
                // La plata se mueve hoy aunque el comprobante tenga otra fecha:
                // el turno que se afecta es el de hoy, y la fecha del documento
                // se guarda aparte para que el cierre muestre el desfase en vez
                // de esconderlo dentro del total.
                .fecha(java.time.LocalDate.now())
                .fechaDocumento(dto.getFecha())
                .origenTipo(MovimientoCajaEntity.ORIGEN_COMPROBANTE)
                .origenId(asiento.getId())
                .origenInferido(origen.turnoInferido())
                .metodoPago(MediosPago.normalizar(dto.getMetodoPago()))
                .build();
        movimientoCajaRepo.save(movimiento);
    }

    /**
     * Todo dinero que se mueve necesita su soporte imprimible, salga de la caja,
     * del banco o de una cuenta contable.
     *
     * <p>El CE/RC contable era el único movimiento que no lo emitía: aparecía en
     * el arqueo pero no en la pantalla de Comprobantes — la misma desde la que se
     * crea. Se emite por el total que se movió, cartera incluida: el soporte
     * ampara la plata entregada o recibida, no la parte que además cruzó cartera.
     *
     * <p>Se le impone el número del asiento a propósito. La serie RC/CE es única
     * para las dos tablas, así que pedir uno nuevo dejaría el mismo pago con
     * RC-000045 en contabilidad y RC-000046 en caja.
     */
    private void emitirComprobanteDeCaja(AsientoContableEntity asiento,
            OrigenFondosService.OrigenFondos origen, String tipo, BigDecimal monto,
            CreateComprobanteDto dto, Integer usuarioId) {
        if (origen == null || monto == null || monto.signum() <= 0) {
            return;
        }
        comprobanteCajaService.sincronizarDeDocumento(
                asiento.getEmpresaId(), usuarioId,
                "CE".equals(tipo) ? "EGRESO" : "INGRESO",
                asiento.getDescripcion(),
                monto,
                MediosPago.normalizar(dto.getMetodoPago()),
                asiento.getBeneficiarioNombre(),
                MovimientoCajaEntity.ORIGEN_COMPROBANTE, asiento.getId(),
                origen.turnoId(),
                asiento.getNumeroComprobante());
    }

    @Override
    public String siguienteConsecutivo(Integer empresaId, String tipoComprobante) {
        String tipo = tipoComprobante != null ? tipoComprobante.trim().toUpperCase() : "CD";
        return queryRepo.siguienteNumeroComprobante(empresaId, tipo);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nombreTercero(TerceroEntity t) {
        if (!isBlank(t.getRazonSocial())) return t.getRazonSocial();
        String nombre = ((t.getNombres() != null ? t.getNombres() : "") + " "
                + (t.getApellidos() != null ? t.getApellidos() : "")).trim();
        return nombre.isEmpty() ? null : nombre;
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        AsientoContableEntity asiento = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
        if (!"MANUAL".equals(asiento.getTipoOrigen())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden anular asientos manuales");
        }
        if ("ANULADO".equals(asiento.getEstado())) {
            return;
        }

        // Anular dejaba el asiento en ANULADO y nada más: el abono que rebajó
        // la cartera y el egreso que salió de la caja seguían vivos. El
        // comprobante desaparecía de la contabilidad pero la deuda seguía
        // rebajada y el arqueo seguía descuadrado.
        //
        // El turno cerrado es la frontera: su arqueo ya se contó y se firmó, y
        // reescribirlo destruye la evidencia de lo que el cajero entregó. Se
        // bloquea la anulación y se remite al ajuste retroactivo, que corrige
        // encima sin tocar el cierre original.
        if (asiento.getTurnoCajaId() != null) {
            TurnoCajaEntity turno = turnoCajaRepo
                    .findByIdAndCajaSucursalEmpresaId(asiento.getTurnoCajaId(), empresaId)
                    .orElse(null);
            if (turno != null && "CERRADA".equals(turno.getEstado())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El comprobante " + asiento.getNumeroComprobante() + " afectó la caja "
                                + turno.getCaja().getNombre() + ", cuyo turno ya está cerrado. "
                                + "Corríjalo con un ajuste retroactivo de caja: reabrir el arqueo "
                                + "borraría la evidencia de lo que el cajero entregó.");
            }
        }

        String referencia = "Comprobante " + asiento.getNumeroComprobante();
        cuentaCobrarService.revertirCrucesDeDocumento(referencia, empresaId);
        cuentaPagarService.revertirCrucesDeDocumento(referencia, empresaId);

        movimientoCajaRepo.deleteAll(
                movimientoCajaRepo.findByOrigenTipoAndOrigenId(
                        MovimientoCajaEntity.ORIGEN_COMPROBANTE, id));

        // El soporte de caja no se borra: se invalida conservando su número, para
        // no dejar un hueco en la serie.
        comprobanteCajaService.anularDeDocumento(empresaId,
                MovimientoCajaEntity.ORIGEN_COMPROBANTE, id,
                "Comprobante contable " + asiento.getNumeroComprobante() + " anulado");

        asiento.setEstado("ANULADO");
        repo.save(asiento);
    }

    @Override
    public BalanceGeneralDto balanceGeneral(Integer empresaId, String hasta) {
        Map<String, Object> saldos = queryRepo.balanceGeneral(empresaId, hasta);

        BigDecimal activo     = getOrZero(saldos, "ACTIVO");
        BigDecimal pasivo     = getOrZero(saldos, "PASIVO");
        BigDecimal patrimonio = getOrZero(saldos, "PATRIMONIO");
        BigDecimal ingreso    = getOrZero(saldos, "INGRESO");
        BigDecimal gasto      = getOrZero(saldos, "GASTO");
        BigDecimal costo      = getOrZero(saldos, "COSTO");

        BigDecimal utilidad = ingreso.subtract(gasto).subtract(costo);
        BigDecimal ecuacion = activo.subtract(pasivo.add(patrimonio).add(utilidad));

        BalanceGeneralDto dto = new BalanceGeneralDto();
        dto.setHasta(hasta);
        dto.setTotalActivo(activo);
        dto.setTotalPasivo(pasivo);
        dto.setTotalPatrimonio(patrimonio);
        dto.setTotalIngreso(ingreso);
        dto.setTotalGasto(gasto);
        dto.setTotalCosto(costo);
        dto.setUtilidadNeta(utilidad);
        dto.setEcuacionContable(ecuacion);
        return dto;
    }

    @Override
    public com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDetalladoDto
            balanceGeneralDetallado(Integer empresaId, String hasta) {

        List<Map<String, Object>> filas = queryRepo.balanceGeneralDetalle(empresaId, hasta);
        Map<String, String> nombresGrupos = queryRepo.nombresGrupos(empresaId);
        Map<String, Object> saldosClase = queryRepo.balanceGeneral(empresaId, hasta);

        BigDecimal resultado = getOrZero(saldosClase, "INGRESO")
                .subtract(getOrZero(saldosClase, "GASTO"))
                .subtract(getOrZero(saldosClase, "COSTO"));

        var activoCorriente   = agruparBalance(filas, "ACTIVO",  g -> g >= 11 && g <= 14, nombresGrupos);
        var activoNoCorriente = agruparBalance(filas, "ACTIVO",  g -> g >= 15 && g <= 19, nombresGrupos);
        var pasivoCorriente   = agruparBalance(filas, "PASIVO",  g -> g >= 21 && g <= 26, nombresGrupos);
        var pasivoNoCorriente = agruparBalance(filas, "PASIVO",  g -> g >= 27 && g <= 29, nombresGrupos);
        var patrimonio        = agruparBalance(filas, "PATRIMONIO", g -> true, nombresGrupos);

        // El resultado del ejercicio (P&G aún no cerrado) se presenta en patrimonio
        // para que la ecuación contable cuadre.
        if (resultado.signum() != 0) {
            patrimonio = new java.util.ArrayList<>(patrimonio);
            patrimonio.add(com.cloud_technological.aura_pos.dto.contabilidad
                    .BalanceGeneralDetalladoDto.GrupoBalanceDto.builder()
                    .codigo("36")
                    .nombre("Resultado del Ejercicio")
                    .saldo(resultado)
                    .cuentas(List.of(com.cloud_technological.aura_pos.dto.contabilidad
                            .BalanceGeneralDetalladoDto.LineaBalanceDto.builder()
                            .codigo("3605").nombre("Utilidad (Pérdida) del Ejercicio").saldo(resultado)
                            .build()))
                    .build());
        }

        BigDecimal totActCte   = sumarGrupos(activoCorriente);
        BigDecimal totActNoCte = sumarGrupos(activoNoCorriente);
        BigDecimal totActivo   = totActCte.add(totActNoCte);
        BigDecimal totPasCte   = sumarGrupos(pasivoCorriente);
        BigDecimal totPasNoCte = sumarGrupos(pasivoNoCorriente);
        BigDecimal totPasivo   = totPasCte.add(totPasNoCte);
        BigDecimal totPatrim   = sumarGrupos(patrimonio);
        BigDecimal totPasPat   = totPasivo.add(totPatrim);
        BigDecimal diferencia  = totActivo.subtract(totPasPat);

        var empresa = empresaRepo.findById(empresaId).orElse(null);

        return com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDetalladoDto.builder()
                .empresaNombre(empresa != null ? empresa.getRazonSocial() : "")
                .nit(empresa != null ? empresa.getNit() : "")
                .fechaCorte(hasta)
                .activoCorriente(activoCorriente)
                .activoNoCorriente(activoNoCorriente)
                .pasivoCorriente(pasivoCorriente)
                .pasivoNoCorriente(pasivoNoCorriente)
                .patrimonio(patrimonio)
                .totalActivoCorriente(totActCte)
                .totalActivoNoCorriente(totActNoCte)
                .totalActivo(totActivo)
                .totalPasivoCorriente(totPasCte)
                .totalPasivoNoCorriente(totPasNoCte)
                .totalPasivo(totPasivo)
                .totalPatrimonio(totPatrim)
                .totalPasivoPatrimonio(totPasPat)
                .resultadoEjercicio(resultado)
                .diferencia(diferencia)
                .cuadra(diferencia.abs().compareTo(BigDecimal.ONE) < 0)
                .build();
    }

    /** Agrupa las cuentas de un tipo por grupo PUC (2 dígitos) que pasa el filtro. */
    private List<com.cloud_technological.aura_pos.dto.contabilidad
            .BalanceGeneralDetalladoDto.GrupoBalanceDto> agruparBalance(
            List<Map<String, Object>> filas, String tipo,
            java.util.function.IntPredicate filtroGrupo, Map<String, String> nombresGrupos) {

        Map<String, List<com.cloud_technological.aura_pos.dto.contabilidad
                .BalanceGeneralDetalladoDto.LineaBalanceDto>> porGrupo = new java.util.LinkedHashMap<>();

        for (Map<String, Object> f : filas) {
            if (!tipo.equals(f.get("tipo"))) continue;
            String codigo = (String) f.get("codigo");
            if (codigo == null || codigo.length() < 2) continue;
            String grupo = codigo.substring(0, 2);
            int grupoNum;
            try {
                grupoNum = Integer.parseInt(grupo);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (!filtroGrupo.test(grupoNum)) continue;

            porGrupo.computeIfAbsent(grupo, k -> new java.util.ArrayList<>())
                    .add(com.cloud_technological.aura_pos.dto.contabilidad
                            .BalanceGeneralDetalladoDto.LineaBalanceDto.builder()
                            .codigo(codigo)
                            .nombre((String) f.get("nombre"))
                            .saldo(toBigDecimal(f.get("saldo")))
                            .build());
        }

        List<com.cloud_technological.aura_pos.dto.contabilidad
                .BalanceGeneralDetalladoDto.GrupoBalanceDto> grupos = new java.util.ArrayList<>();
        porGrupo.forEach((grupo, cuentas) -> {
            BigDecimal saldo = cuentas.stream()
                    .map(com.cloud_technological.aura_pos.dto.contabilidad
                            .BalanceGeneralDetalladoDto.LineaBalanceDto::getSaldo)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            grupos.add(com.cloud_technological.aura_pos.dto.contabilidad
                    .BalanceGeneralDetalladoDto.GrupoBalanceDto.builder()
                    .codigo(grupo)
                    .nombre(nombresGrupos.getOrDefault(grupo, "Grupo " + grupo))
                    .saldo(saldo)
                    .cuentas(cuentas)
                    .build());
        });
        return grupos;
    }

    private BigDecimal sumarGrupos(List<com.cloud_technological.aura_pos.dto.contabilidad
            .BalanceGeneralDetalladoDto.GrupoBalanceDto> grupos) {
        return grupos.stream()
                .map(com.cloud_technological.aura_pos.dto.contabilidad
                        .BalanceGeneralDetalladoDto.GrupoBalanceDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }

    @Override
    public EstadoResultadosDto estadoResultados(Integer empresaId, String desde, String hasta,
            Long centroCostoId, Long proyectoId, Long frenteId) {
        List<EstadoResultadosLineaDto> lineas = queryRepo.estadoResultados(
                empresaId, desde, hasta, centroCostoId, proyectoId, frenteId);

        List<EstadoResultadosLineaDto> ingresos = lineas.stream()
                .filter(l -> "INGRESO".equals(l.getTipo())).collect(Collectors.toList());
        List<EstadoResultadosLineaDto> costos = lineas.stream()
                .filter(l -> "COSTO".equals(l.getTipo())).collect(Collectors.toList());
        List<EstadoResultadosLineaDto> gastos = lineas.stream()
                .filter(l -> "GASTO".equals(l.getTipo())).collect(Collectors.toList());

        BigDecimal totalIngresos = sum(ingresos);
        BigDecimal totalCostos   = sum(costos);
        BigDecimal totalGastos   = sum(gastos);
        BigDecimal utilidadBruta = totalIngresos.subtract(totalCostos);
        BigDecimal utilidadNeta  = utilidadBruta.subtract(totalGastos);

        BigDecimal margenBruto = totalIngresos.compareTo(BigDecimal.ZERO) != 0
                ? utilidadBruta.multiply(new BigDecimal("100")).divide(totalIngresos, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal margenNeto = totalIngresos.compareTo(BigDecimal.ZERO) != 0
                ? utilidadNeta.multiply(new BigDecimal("100")).divide(totalIngresos, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        EstadoResultadosDto dto = new EstadoResultadosDto();
        dto.setDesde(desde);
        dto.setHasta(hasta);
        dto.setIngresos(ingresos);
        dto.setCostos(costos);
        dto.setGastos(gastos);
        dto.setTotalIngresos(totalIngresos);
        dto.setTotalCostos(totalCostos);
        dto.setTotalGastos(totalGastos);
        dto.setUtilidadBruta(utilidadBruta);
        dto.setUtilidadNeta(utilidadNeta);
        dto.setMargenBruto(margenBruto);
        dto.setMargenNeto(margenNeto);
        return dto;
    }

    @Override
    public List<LibroMayorLineaDto> libroMayor(Integer empresaId, Long cuentaId,
            String desde, String hasta, Long centroCostoId, Long proyectoId, Long frenteId) {
        return queryRepo.libroMayor(empresaId, cuentaId, desde, hasta,
                centroCostoId, proyectoId, frenteId);
    }

    @Override
    public FlujoCajaDto flujoCaja(Integer empresaId, String desde, String hasta) {
        BigDecimal saldoInicial = queryRepo.saldoInicialTesoreria(empresaId, desde);
        List<FlujoCajaLineaDto> movimientos = queryRepo.movimientosTesoreria(empresaId, desde, hasta);

        BigDecimal totalIngresos = movimientos.stream()
                .filter(m -> "INGRESO".equals(m.getTipo()))
                .map(FlujoCajaLineaDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgresos = movimientos.stream()
                .filter(m -> "EGRESO".equals(m.getTipo()))
                .map(FlujoCajaLineaDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoFinal = saldoInicial.add(totalIngresos).subtract(totalEgresos);

        List<FlujoCajaProyeccionDto> cxc = queryRepo.proyeccionCxC(empresaId);
        List<FlujoCajaProyeccionDto> cxp = queryRepo.proyeccionCxP(empresaId);

        BigDecimal totalPorCobrar = cxc.stream()
                .map(FlujoCajaProyeccionDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPorPagar = cxp.stream()
                .map(FlujoCajaProyeccionDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FlujoCajaDto.builder()
                .desde(desde)
                .hasta(hasta)
                .saldoInicial(saldoInicial)
                .movimientos(movimientos)
                .totalIngresos(totalIngresos)
                .totalEgresos(totalEgresos)
                .saldoFinal(saldoFinal)
                .proyeccionCxC(cxc)
                .proyeccionCxP(cxp)
                .totalPorCobrar(totalPorCobrar)
                .totalPorPagar(totalPorPagar)
                .build();
    }

    private BigDecimal sum(List<EstadoResultadosLineaDto> lineas) {
        return lineas.stream()
                .map(l -> l.getSaldo() != null ? l.getSaldo() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getOrZero(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    private AsientoContableTableDto toTableDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setTipoComprobante(e.getTipoComprobante());
        dto.setBeneficiarioTerceroId(e.getBeneficiarioTerceroId());
        dto.setBeneficiarioNombre(e.getBeneficiarioNombre());
        dto.setBeneficiarioDireccion(e.getBeneficiarioDireccion());
        dto.setBeneficiarioTelefono(e.getBeneficiarioTelefono());
        dto.setCiudad(e.getCiudad());
        dto.setFechaVencimiento(e.getFechaVencimiento() != null ? e.getFechaVencimiento().toString() : null);
        return dto;
    }
}
