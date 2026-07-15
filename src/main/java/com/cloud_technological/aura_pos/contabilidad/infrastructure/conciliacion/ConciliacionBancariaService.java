package com.cloud_technological.aura_pos.contabilidad.infrastructure.conciliacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.contabilidad.MovimientoLibroDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity;
import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ConciliacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExtractoBancarioJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExtractoLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Conciliación bancaria (E9 · C6): extracto importado por CSV genérico
 * (fecha, descripción, valor con el signo del banco), matching sugerido por
 * valor exacto y fecha ±3 días contra el libro de la cuenta contable del
 * banco, ajustes contabilizados desde la misma pantalla (comisiones, GMF,
 * intereses) y cierre que exige que el saldo conciliado explique el saldo
 * final del extracto.
 */
@Service
@RequiredArgsConstructor
public class ConciliacionBancariaService {

    public static final String TIPO_ORIGEN_AJUSTE = "AJUSTE_BANCARIO";

    /** Tolerancia de fechas del matching sugerido (días). */
    private static final int DIAS_MATCHING = 3;

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private final ExtractoBancarioJPARepository extractoRepo;
    private final ExtractoLineaJPARepository lineaRepo;
    private final CuentaBancariaJPARepository cuentaBancariaRepo;
    private final ConciliacionQueryRepository libro;
    private final AsientoContableJPARepository asientoRepo;
    private final ApplicationEventPublisher eventPublisher;

    // ── Extractos ────────────────────────────────────────────────────────

    @Transactional
    public ExtractoBancarioEntity crear(Integer empresaId, Long usuarioId,
            Long cuentaBancariaId, String periodo, BigDecimal saldoInicial, BigDecimal saldoFinal) {
        YearMonth ym = parsearPeriodo(periodo);
        CuentaBancariaEntity cuenta = cuentaBancaria(empresaId, cuentaBancariaId);
        if (cuenta.getCuentaContableId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La cuenta bancaria '" + cuenta.getNombre() + "' no tiene cuenta contable "
                            + "asociada. Asóciela en Tesorería antes de conciliar.");
        }
        if (extractoRepo.existsByEmpresaIdAndCuentaBancariaIdAndPeriodo(
                empresaId, cuentaBancariaId, ym.toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un extracto de esa cuenta para el período " + ym + ".");
        }
        return extractoRepo.save(ExtractoBancarioEntity.builder()
                .empresaId(empresaId)
                .cuentaBancariaId(cuentaBancariaId)
                .periodo(ym.toString())
                .saldoInicial(ReglasAsiento.nz(saldoInicial))
                .saldoFinal(ReglasAsiento.nz(saldoFinal))
                .usuarioId(usuarioId)
                .build());
    }

    public List<ExtractoBancarioEntity> listar(Integer empresaId, Long cuentaBancariaId) {
        return cuentaBancariaId != null
                ? extractoRepo.findByEmpresaIdAndCuentaBancariaIdOrderByPeriodoDesc(empresaId, cuentaBancariaId)
                : extractoRepo.findByEmpresaIdOrderByPeriodoDesc(empresaId);
    }

    public ExtractoBancarioEntity obtener(Integer empresaId, Long extractoId) {
        return extractoRepo.findByIdAndEmpresaId(extractoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Extracto bancario no encontrado"));
    }

    public List<ExtractoLineaEntity> lineas(Integer empresaId, Long extractoId) {
        obtener(empresaId, extractoId);
        return lineaRepo.findByExtractoIdOrderByFechaAscIdAsc(extractoId);
    }

    /**
     * Elimina un extracto creado por error. Solo ABIERTO y sin líneas de
     * ajuste: esas ya tienen asiento contabilizado y borrarlas dejaría el
     * asiento sin su documento origen.
     */
    @Transactional
    public void eliminar(Integer empresaId, Long extractoId) {
        ExtractoBancarioEntity extracto = abierto(empresaId, extractoId);
        List<ExtractoLineaEntity> lineas = lineaRepo.findByExtractoIdOrderByFechaAscIdAsc(extractoId);
        boolean tieneAjustes = lineas.stream()
                .anyMatch(l -> ExtractoLineaEntity.ESTADO_AJUSTE.equals(l.getEstado()));
        if (tieneAjustes) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El extracto tiene líneas de ajuste ya contabilizadas: anule esos "
                            + "asientos por contraasiento antes de eliminarlo.");
        }
        lineaRepo.deleteAll(lineas);
        extractoRepo.delete(extracto);
    }

    // ── Importación de líneas ────────────────────────────────────────────

    /** Línea ya estructurada (el front también puede parsear su Excel). */
    public record LineaImportada(LocalDate fecha, String descripcion, BigDecimal valor) {
    }

    @Transactional
    public List<ExtractoLineaEntity> importar(Integer empresaId, Long extractoId,
            String csv, List<LineaImportada> lineas) {
        ExtractoBancarioEntity extracto = abierto(empresaId, extractoId);
        List<LineaImportada> aImportar = new ArrayList<>();
        if (lineas != null) {
            aImportar.addAll(lineas);
        }
        if (csv != null && !csv.isBlank()) {
            aImportar.addAll(parsearCsv(csv));
        }
        if (aImportar.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No hay líneas para importar: envíe 'csv' o 'lineas'.");
        }
        List<ExtractoLineaEntity> guardadas = new ArrayList<>();
        for (LineaImportada l : aImportar) {
            if (l.fecha() == null || l.valor() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Toda línea del extracto requiere fecha y valor.");
            }
            guardadas.add(lineaRepo.save(ExtractoLineaEntity.builder()
                    .extractoId(extracto.getId())
                    .fecha(l.fecha())
                    .descripcion(l.descripcion())
                    .valor(l.valor())
                    .build()));
        }
        return guardadas;
    }

    // ── Matching ─────────────────────────────────────────────────────────

    /** Sugerencias por línea pendiente: valor exacto y fecha ±3 días. */
    public List<Map<String, Object>> sugerencias(Integer empresaId, Long extractoId) {
        ExtractoBancarioEntity extracto = obtener(empresaId, extractoId);
        List<MovimientoLibroDto> movimientos = movimientosDelPeriodo(empresaId, extracto, DIAS_MATCHING);

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (ExtractoLineaEntity linea : lineaRepo.findByExtractoIdOrderByFechaAscIdAsc(extractoId)) {
            if (!ExtractoLineaEntity.ESTADO_PENDIENTE.equals(linea.getEstado())) {
                continue;
            }
            List<MovimientoLibroDto> candidatos = movimientos.stream()
                    .filter(m -> !m.conciliado())
                    .filter(m -> coincideValor(linea, m))
                    .filter(m -> Math.abs(m.fecha().toEpochDay() - linea.getFecha().toEpochDay()) <= DIAS_MATCHING)
                    .toList();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("linea", linea);
            r.put("candidatos", candidatos);
            resultado.add(r);
        }
        return resultado;
    }

    /** Columna "libro" de la pantalla: movimientos del período con su marca. */
    public List<MovimientoLibroDto> movimientosLibro(Integer empresaId, Long extractoId) {
        return movimientosDelPeriodo(empresaId, obtener(empresaId, extractoId), 0);
    }

    @Transactional
    public ExtractoLineaEntity conciliar(Integer empresaId, Long extractoId,
            Long lineaId, Long asientoDetalleId) {
        ExtractoBancarioEntity extracto = abierto(empresaId, extractoId);
        ExtractoLineaEntity linea = lineaPendiente(extractoId, lineaId);
        Long cuentaContableId = cuentaBancaria(empresaId, extracto.getCuentaBancariaId())
                .getCuentaContableId();
        MovimientoLibroDto mov = libro.movimiento(empresaId, cuentaContableId, asientoDetalleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El movimiento del libro no existe, no está contabilizado o no es "
                                + "de la cuenta contable de este banco."));
        if (lineaRepo.existsByAsientoDetalleId(asientoDetalleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ese movimiento del libro ya está conciliado con otra línea de extracto.");
        }
        if (!coincideValor(linea, mov)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El valor del movimiento no coincide con el de la línea del extracto ("
                            + linea.getValor() + ").");
        }
        linea.setAsientoDetalleId(asientoDetalleId);
        linea.setEstado(ExtractoLineaEntity.ESTADO_CONCILIADO);
        return lineaRepo.save(linea);
    }

    @Transactional
    public ExtractoLineaEntity desconciliar(Integer empresaId, Long extractoId, Long lineaId) {
        abierto(empresaId, extractoId);
        ExtractoLineaEntity linea = linea(extractoId, lineaId);
        if (ExtractoLineaEntity.ESTADO_AJUSTE.equals(linea.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La línea ya generó un asiento de ajuste; anule el asiento por "
                            + "contraasiento si el ajuste fue un error.");
        }
        linea.setAsientoDetalleId(null);
        linea.setEstado(ExtractoLineaEntity.ESTADO_PENDIENTE);
        return lineaRepo.save(linea);
    }

    // ── Ajustes desde la pantalla (comisiones, GMF, intereses) ───────────

    @Transactional
    public ExtractoLineaEntity registrarAjuste(Integer empresaId, Long usuarioId,
            Long extractoId, Long lineaId, String tipoAjuste) {
        abierto(empresaId, extractoId);
        if (!ExtractoLineaEntity.AJUSTE_GASTO_BANCARIO.equals(tipoAjuste)
                && !ExtractoLineaEntity.AJUSTE_GMF.equals(tipoAjuste)
                && !ExtractoLineaEntity.AJUSTE_INTERES.equals(tipoAjuste)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de ajuste inválido: use GASTO_BANCARIO, GMF o INTERES.");
        }
        ExtractoLineaEntity linea = lineaPendiente(extractoId, lineaId);
        linea.setTipoAjuste(tipoAjuste);
        linea.setEstado(ExtractoLineaEntity.ESTADO_AJUSTE);
        ExtractoLineaEntity guardada = lineaRepo.save(linea);
        // El asiento nace por el registry (AFTER_COMMIT), idempotente por línea.
        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                TIPO_ORIGEN_AJUSTE, guardada.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));
        return guardada;
    }

    // ── Resumen y cierre ─────────────────────────────────────────────────

    public Map<String, Object> resumen(Integer empresaId, Long extractoId) {
        ExtractoBancarioEntity extracto = obtener(empresaId, extractoId);
        List<ExtractoLineaEntity> lineas = lineaRepo.findByExtractoIdOrderByFechaAscIdAsc(extractoId);

        BigDecimal sumaValores = BigDecimal.ZERO;
        BigDecimal saldoConciliado = extracto.getSaldoInicial();
        long pendientes = 0, conciliadas = 0, ajustes = 0;
        for (ExtractoLineaEntity l : lineas) {
            sumaValores = sumaValores.add(l.getValor());
            switch (l.getEstado()) {
                case ExtractoLineaEntity.ESTADO_PENDIENTE -> pendientes++;
                case ExtractoLineaEntity.ESTADO_CONCILIADO -> {
                    conciliadas++;
                    saldoConciliado = saldoConciliado.add(l.getValor());
                }
                case ExtractoLineaEntity.ESTADO_AJUSTE -> {
                    ajustes++;
                    saldoConciliado = saldoConciliado.add(l.getValor());
                }
                default -> { }
            }
        }
        // Partidas en tránsito: movimientos del libro del período que el banco
        // aún no refleja (informativo). Los asientos de ajuste se excluyen —
        // ya están representados por su línea AJUSTE.
        List<MovimientoLibroDto> transito = movimientosDelPeriodo(empresaId, extracto, 0).stream()
                .filter(m -> !m.conciliado())
                .filter(m -> !TIPO_ORIGEN_AJUSTE.equals(m.tipoOrigen()))
                .toList();
        BigDecimal netoTransito = transito.stream()
                .map(m -> ReglasAsiento.nz(m.debito()).subtract(ReglasAsiento.nz(m.credito())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal diferencia = extracto.getSaldoFinal().subtract(saldoConciliado);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("extracto", extracto);
        r.put("totalLineas", lineas.size());
        r.put("pendientes", pendientes);
        r.put("conciliadas", conciliadas);
        r.put("ajustes", ajustes);
        r.put("sumaValores", sumaValores);
        r.put("saldoConciliado", saldoConciliado);
        r.put("diferencia", diferencia);
        r.put("partidasEnTransito", transito);
        r.put("netoPartidasEnTransito", netoTransito);
        r.put("puedeCerrar", pendientes == 0 && diferencia.signum() == 0 && !lineas.isEmpty());
        return r;
    }

    /**
     * Cierra el extracto: exige todo conciliado y que el saldo inicial más las
     * líneas conciliadas/ajustadas expliquen exactamente el saldo final.
     */
    @Transactional
    public ExtractoBancarioEntity cerrar(Integer empresaId, Long extractoId) {
        ExtractoBancarioEntity extracto = abierto(empresaId, extractoId);
        List<ExtractoLineaEntity> lineas = lineaRepo.findByExtractoIdOrderByFechaAscIdAsc(extractoId);
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El extracto no tiene líneas importadas.");
        }
        long pendientes = lineas.stream()
                .filter(l -> ExtractoLineaEntity.ESTADO_PENDIENTE.equals(l.getEstado())).count();
        if (pendientes > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Quedan " + pendientes + " líneas del extracto sin conciliar.");
        }
        BigDecimal saldoConciliado = extracto.getSaldoInicial();
        for (ExtractoLineaEntity l : lineas) {
            saldoConciliado = saldoConciliado.add(l.getValor());
            if (ExtractoLineaEntity.ESTADO_AJUSTE.equals(l.getEstado())
                    && asientoRepo.findByTipoOrigenAndOrigenIdAndEmpresaId(
                            TIPO_ORIGEN_AJUSTE, l.getId(), empresaId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El ajuste de la línea #" + l.getId() + " aún no tiene asiento "
                                + "(revise el posting log y reintente el ajuste).");
            }
        }
        if (saldoConciliado.compareTo(extracto.getSaldoFinal()) != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El saldo conciliado (" + saldoConciliado + ") no coincide con el saldo "
                            + "final del extracto (" + extracto.getSaldoFinal() + ").");
        }
        extracto.setEstado(ExtractoBancarioEntity.ESTADO_CONCILIADO);
        extracto.setConciliadoAt(LocalDateTime.now());
        return extractoRepo.save(extracto);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<MovimientoLibroDto> movimientosDelPeriodo(Integer empresaId,
            ExtractoBancarioEntity extracto, int diasHolgura) {
        YearMonth ym = parsearPeriodo(extracto.getPeriodo());
        Long cuentaContableId = cuentaBancaria(empresaId, extracto.getCuentaBancariaId())
                .getCuentaContableId();
        return libro.movimientosLibro(empresaId, cuentaContableId,
                ym.atDay(1).minusDays(diasHolgura), ym.atEndOfMonth().plusDays(diasHolgura));
    }

    /** valor >0 del extracto = entra dinero = débito del libro; <0 = crédito. */
    private static boolean coincideValor(ExtractoLineaEntity linea, MovimientoLibroDto mov) {
        BigDecimal valor = linea.getValor();
        if (valor.signum() > 0) {
            return ReglasAsiento.nz(mov.debito()).compareTo(valor) == 0;
        }
        return ReglasAsiento.nz(mov.credito()).compareTo(valor.negate()) == 0;
    }

    private ExtractoBancarioEntity abierto(Integer empresaId, Long extractoId) {
        ExtractoBancarioEntity extracto = obtener(empresaId, extractoId);
        if (!ExtractoBancarioEntity.ESTADO_ABIERTO.equals(extracto.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El extracto ya está conciliado y no admite cambios.");
        }
        return extracto;
    }

    private ExtractoLineaEntity linea(Long extractoId, Long lineaId) {
        return lineaRepo.findByIdAndExtractoId(lineaId, extractoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Línea de extracto no encontrada"));
    }

    private ExtractoLineaEntity lineaPendiente(Long extractoId, Long lineaId) {
        ExtractoLineaEntity linea = linea(extractoId, lineaId);
        if (!ExtractoLineaEntity.ESTADO_PENDIENTE.equals(linea.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La línea ya está conciliada o ajustada; desconcíliela primero.");
        }
        return linea;
    }

    private CuentaBancariaEntity cuentaBancaria(Integer empresaId, Long cuentaBancariaId) {
        return cuentaBancariaRepo.findByIdAndEmpresaId(cuentaBancariaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta bancaria no encontrada"));
    }

    private static YearMonth parsearPeriodo(String periodo) {
        try {
            return YearMonth.parse(periodo);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Período inválido: use el formato yyyy-MM (ej. 2026-07).");
        }
    }

    // ── Parser CSV genérico: fecha, descripción, valor ───────────────────

    static List<LineaImportada> parsearCsv(String csv) {
        List<LineaImportada> lineas = new ArrayList<>();
        String[] filas = csv.split("\r?\n");
        int numero = 0;
        for (String fila : filas) {
            numero++;
            if (fila.isBlank()) {
                continue;
            }
            String sep = detectarSeparador(fila);
            String[] cols = fila.split(java.util.regex.Pattern.quote(sep), -1);
            if (cols.length < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fila " + numero + ": se esperan al menos 3 columnas "
                                + "(fecha" + sep + "descripción" + sep + "valor).");
            }
            LocalDate fecha = parsearFecha(cols[0].trim());
            if (fecha == null) {
                if (lineas.isEmpty()) {
                    continue;   // encabezado
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fila " + numero + ": fecha inválida '" + cols[0].trim() + "'.");
            }
            String descripcion = String.join(" ",
                    java.util.Arrays.asList(cols).subList(1, cols.length - 1)).trim();
            BigDecimal valor = parsearValor(cols[cols.length - 1], numero);
            lineas.add(new LineaImportada(fecha, descripcion, valor));
        }
        return lineas;
    }

    private static String detectarSeparador(String fila) {
        if (fila.contains(";")) {
            return ";";
        }
        if (fila.contains("\t")) {
            return "\t";
        }
        return ",";
    }

    private static LocalDate parsearFecha(String texto) {
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(texto, f);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }

    private static BigDecimal parsearValor(String texto, int numeroFila) {
        String s = texto.replace("$", "").replace("\"", "").replace(" ", "").trim();
        boolean coma = s.contains(","), punto = s.contains(".");
        if (coma && punto) {
            // El separador que aparece de último es el decimal.
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (coma) {
            int idx = s.lastIndexOf(',');
            boolean decimal = s.indexOf(',') == idx && s.length() - idx - 1 <= 2;
            s = decimal ? s.replace(',', '.') : s.replace(",", "");
        } else if (punto && s.indexOf('.') != s.lastIndexOf('.')) {
            s = s.replace(".", "");   // varios puntos = separador de miles (1.234.567)
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fila " + numeroFila + ": valor inválido '" + texto.trim() + "'.");
        }
    }
}
