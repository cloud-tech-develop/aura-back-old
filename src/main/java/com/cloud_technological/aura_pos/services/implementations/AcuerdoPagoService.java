package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoCuotaDto;
import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoPagoDto;
import com.cloud_technological.aura_pos.dto.cartera.acuerdo.CreateAcuerdoPagoDto;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.GestionCobroEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.cartera.AcuerdoPagoQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.GestionCobroJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CarteraService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Acuerdos de pago: la deuda de varias cuentas repartida en cuotas.
 *
 * <p>Las cuotas no reciben pagos directos. El cliente sigue pagando las cuentas
 * (recibo, abono o cruce de anticipo) y lo que haya bajado su saldo desde el
 * acuerdo se reparte sobre las cuotas en orden. Así arqueo, asientos y reportes
 * no cambian, y anular un pago devuelve la cuota a pendiente sin hacer nada más.
 *
 * <p>Mientras el acuerdo está vivo, las cuentas vencen con la primera cuota sin
 * pagar: la mora, las edades, el score y la suspensión por mora miden la cuota,
 * no la factura original. Una cuota pasada su fecha más los días de gracia deja
 * el acuerdo INCUMPLIDO; si el cliente se pone al día vuelve a VIGENTE.
 */
@Slf4j
@Service
public class AcuerdoPagoService {

    private static final String PREFIJO = "ACP";
    private static final int MAX_CUOTAS = 60;
    private static final int MAX_DIAS_GRACIA = 60;
    private static final Set<String> FRECUENCIAS = Set.of("SEMANAL", "QUINCENAL", "MENSUAL", "PERSONALIZADA");

    @Autowired
    private AcuerdoPagoQueryRepository queryRepo;

    @Autowired
    private CuentaCobrarJPARepository cuentaRepo;

    @Autowired
    private GestionCobroJPARepository gestionRepo;

    @Autowired
    private TerceroJPARepository terceroRepo;

    @Autowired
    private EmpresaJPARepository empresaRepo;

    @Autowired
    private UsuarioJPARepository usuarioRepo;

    @Autowired
    @Lazy
    private PromesaPagoService promesaPagoService;

    @Autowired
    @Lazy
    private CarteraService carteraService;

    @Transactional
    public AcuerdoPagoDto crear(CreateAcuerdoPagoDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getTerceroId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Seleccione el cliente");
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        // Las cuentas se bloquean en orden de id, como en el recibo de caja.
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(
                dto.getCuentaCobrarIds() != null ? dto.getCuentaCobrarIds() : List.of()));
        ids.removeIf(java.util.Objects::isNull);
        if (ids.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Seleccione las facturas que entran al acuerdo");
        ids.sort(null);

        List<CuentaCobrarEntity> cuentas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            CuentaCobrarEntity c = cuentaRepo.bloquear(id, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar #" + id + " no encontrada"));
            if (c.getTercero() == null || !dto.getTerceroId().equals(c.getTercero().getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta " + c.getNumeroCuenta() + " es de otro cliente");
            if (c.getDeletedAt() != null || "anulada".equals(c.getEstado()) || "pagada".equals(c.getEstado())
                    || c.getSaldoPendiente() == null || c.getSaldoPendiente().signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta " + c.getNumeroCuenta() + " ya no tiene saldo");
            String otro = queryRepo.acuerdoActivoDeCuenta(id);
            if (otro != null)
                throw new GlobalException(HttpStatus.CONFLICT,
                        "La cuenta " + c.getNumeroCuenta() + " ya está en el acuerdo " + otro + ": anúlelo primero");
            cuentas.add(c);
            total = total.add(c.getSaldoPendiente());
        }

        List<CreateAcuerdoPagoDto.Cuota> cuotas = dto.getCuotas() != null ? dto.getCuotas() : List.of();
        if (cuotas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El acuerdo necesita al menos una cuota");
        if (cuotas.size() > MAX_CUOTAS)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_CUOTAS + " cuotas por acuerdo");
        LocalDate hoy = LocalDate.now();
        LocalDate anterior = null;
        BigDecimal sumaCuotas = BigDecimal.ZERO;
        for (int i = 0; i < cuotas.size(); i++) {
            CreateAcuerdoPagoDto.Cuota q = cuotas.get(i);
            if (q.getFechaVencimiento() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuota " + (i + 1) + " no tiene fecha");
            if (q.getFechaVencimiento().isBefore(hoy))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuota " + (i + 1) + " tiene una fecha pasada");
            if (anterior != null && !q.getFechaVencimiento().isAfter(anterior))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuota " + (i + 1) + " debe vencer después de la cuota " + i);
            BigDecimal valor = escala(q.getValor());
            if (valor.signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuota " + (i + 1) + " debe tener un valor mayor a 0");
            q.setValor(valor);
            sumaCuotas = sumaCuotas.add(valor);
            anterior = q.getFechaVencimiento();
        }
        if (sumaCuotas.compareTo(total) != 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las cuotas suman $" + sumaCuotas.toPlainString() + " y la deuda del acuerdo es $"
                            + total.toPlainString() + ": deben ser iguales");

        String frecuencia = dto.getFrecuencia() != null ? dto.getFrecuencia().trim().toUpperCase() : "PERSONALIZADA";
        if (!FRECUENCIAS.contains(frecuencia)) frecuencia = "PERSONALIZADA";
        int diasGracia = dto.getDiasGracia() != null ? dto.getDiasGracia() : 0;
        if (diasGracia < 0 || diasGracia > MAX_DIAS_GRACIA)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días de gracia van de 0 a " + MAX_DIAS_GRACIA);

        int consecutivo = queryRepo.siguienteConsecutivo(empresaId);
        String numero = String.format("%s-%06d", PREFIJO, consecutivo);
        Long acuerdoId = queryRepo.insertarAcuerdo(empresaId, dto.getTerceroId(), numero, consecutivo, total,
                cuotas.size(), frecuencia, diasGracia, recortar(dto.getObservaciones(), 500), usuarioId.intValue());
        for (CuentaCobrarEntity c : cuentas)
            queryRepo.insertarCuenta(acuerdoId, c.getId(), c.getSaldoPendiente(), c.getFechaVencimiento());
        for (int i = 0; i < cuotas.size(); i++)
            queryRepo.insertarCuota(acuerdoId, i + 1, cuotas.get(i).getFechaVencimiento(), cuotas.get(i).getValor());

        // El acuerdo reemplaza cualquier promesa suelta: queda en la línea de tiempo del cliente.
        promesaPagoService.cancelarPendientes(dto.getTerceroId(), empresaId);
        DateTimeFormatter dia = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        gestionRepo.save(GestionCobroEntity.builder()
                .empresa(empresaRepo.getReferenceById(empresaId))
                .tercero(tercero)
                .cuentaCobrar(cuentas.size() == 1 ? cuentas.get(0) : null)
                .tipoGestion("ACUERDO_PAGO")
                .resultado("CONTACTADO")
                .nota("Acuerdo " + numero + ": $" + total.toPlainString() + " en " + cuotas.size()
                        + (cuotas.size() == 1 ? " cuota" : " cuotas") + ", del "
                        + cuotas.get(0).getFechaVencimiento().format(dia) + " al "
                        + cuotas.get(cuotas.size() - 1).getFechaVencimiento().format(dia)
                        + (dto.getObservaciones() != null && !dto.getObservaciones().isBlank()
                                ? ". " + dto.getObservaciones().trim() : ""))
                .usuario(usuarioRepo.findById(usuarioId.intValue()).orElse(null))
                .build());
        gestionRepo.flush();

        // Mueve el vencimiento de las cuentas a la primera cuota.
        evaluar(empresaId, null, acuerdoId);
        recalcularScore(dto.getTerceroId(), empresaId);
        return queryRepo.obtener(acuerdoId, empresaId);
    }

    @Transactional
    public AcuerdoPagoDto anular(Long id, String motivo, Integer empresaId, Long usuarioId) {
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique por qué se anula el acuerdo");
        Map<String, Object> fila = queryRepo.bloquear(id, empresaId);
        if (fila == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Acuerdo de pago no encontrado");
        String estado = (String) fila.get("estado");
        if ("ANULADO".equals(estado))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El acuerdo ya está anulado");
        if ("CUMPLIDO".equals(estado))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El acuerdo ya se cumplió: no hay nada que anular");
        queryRepo.anular(id, recortar(motivo, 300), usuarioId.intValue());
        Long terceroId = ((Number) fila.get("tercero_id")).longValue();
        recalcularScore(terceroId, empresaId);
        return queryRepo.obtener(id, empresaId);
    }

    @Transactional
    public AcuerdoPagoDto obtener(Long id, Integer empresaId) {
        evaluar(empresaId, null, id);
        AcuerdoPagoDto dto = queryRepo.obtener(id, empresaId);
        if (dto == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Acuerdo de pago no encontrado");
        return dto;
    }

    @Transactional
    public PageImpl<AcuerdoPagoDto> listar(Integer empresaId, String estado, String search, int page, int rows) {
        evaluar(empresaId, null, null);
        return queryRepo.listar(empresaId, estado != null && !estado.isBlank() ? estado : null, search,
                Math.max(page, 0), Math.min(Math.max(rows, 1), 200));
    }

    public List<AcuerdoPagoDto> delCliente(Long terceroId, Integer empresaId) {
        return queryRepo.delCliente(terceroId, empresaId);
    }

    /**
     * Reparte lo pagado sobre las cuotas, fija el estado del acuerdo y mueve el
     * vencimiento de sus cuentas. Idempotente: se llama al pagar, al anular un
     * pago, al abrir la ficha o la campana, y en la pasada nocturna.
     *
     * <p>Quien la llame después de modificar cuentas con JPA debe hacer flush
     * antes: se lee el saldo por JDBC.
     *
     * @param empresaId null = todas (solo el job nocturno).
     * @return cuántos acuerdos cambiaron de estado.
     */
    @Transactional
    public int evaluar(Integer empresaId, Long terceroId, Long acuerdoId) {
        LocalDate hoy = LocalDate.now();
        int cambios = 0;
        Set<String> recalcular = new HashSet<>();
        for (Map<String, Object> a : queryRepo.vivos(empresaId, terceroId, acuerdoId)) {
            Long id = ((Number) a.get("id")).longValue();
            BigDecimal total = (BigDecimal) a.get("valor_total");
            int gracia = ((Number) a.get("dias_gracia")).intValue();
            String estadoAnterior = (String) a.get("estado");

            BigDecimal pagado = queryRepo.reducido(id).min(total);
            BigDecimal restante = pagado;
            LocalDate primeraSinPagar = null;
            boolean vencida = false;
            for (AcuerdoCuotaDto q : queryRepo.cuotas(id)) {
                BigDecimal aplicado = restante.min(q.getValor());
                restante = restante.subtract(aplicado);
                String estado;
                if (aplicado.compareTo(q.getValor()) >= 0) {
                    estado = "PAGADA";
                } else {
                    if (primeraSinPagar == null) primeraSinPagar = q.getFechaVencimiento();
                    if (hoy.isAfter(q.getFechaVencimiento().plusDays(gracia))) {
                        estado = "VENCIDA";
                        vencida = true;
                    } else {
                        estado = aplicado.signum() > 0 ? "PARCIAL" : "PENDIENTE";
                    }
                }
                queryRepo.actualizarCuota(q.getId(), aplicado, estado);
            }

            String estado = primeraSinPagar == null ? "CUMPLIDO" : vencida ? "INCUMPLIDO" : "VIGENTE";
            queryRepo.actualizarAcuerdo(id, pagado, estado);
            if (primeraSinPagar != null) queryRepo.moverVencimientoCuentas(id, primeraSinPagar);

            if (!estado.equals(estadoAnterior)) {
                cambios++;
                if ("INCUMPLIDO".equals(estado))
                    recalcular.add(a.get("empresa_id") + ":" + a.get("tercero_id"));
            }
        }
        // Incumplir baja el score y puede suspender el crédito.
        for (String clave : recalcular) {
            String[] partes = clave.split(":");
            recalcularScore(Long.valueOf(partes[1]), Integer.valueOf(partes[0]));
        }
        return cambios;
    }

    private void recalcularScore(Long terceroId, Integer empresaId) {
        try {
            carteraService.recalcularScore(terceroId, empresaId);
        } catch (Exception e) {
            log.warn("No se pudo recalcular el score del tercero {} (empresa {}): {}", terceroId, empresaId, e.getMessage());
        }
    }

    private static BigDecimal escala(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
