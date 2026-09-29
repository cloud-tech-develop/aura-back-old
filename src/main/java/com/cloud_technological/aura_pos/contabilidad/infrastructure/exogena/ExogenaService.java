package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;
import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaErrorEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLineaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaConceptoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaErrorJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaFormatoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaLoteJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaMapeoCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Información exógena DIAN (E11 · C7): mapeos cuenta→concepto por empresa,
 * validador previo (terceros incompletos, cuentas sin mapeo, borradores,
 * períodos abiertos), generación de lotes versionados con cuantías menores y
 * aprobación que bloquea el lote. La aritmética vive en
 * {@link ExogenaCalculadora}; el Excel del prevalidador en
 * {@link ExogenaExcelExporter}.
 */
@Service
@RequiredArgsConstructor
public class ExogenaService {

    public static final BigDecimal UMBRAL_CUANTIA_MENOR_DEFAULT = new BigDecimal("100000");

    /** Mapeos default sobre el PUC seed: formato, concepto, prefijo, tipo de valor. */
    private static final String[][] MAPEOS_DEFAULT = {
            { "1001", "5001", "5105",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5006", "5305",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5007", "1435",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5008", "15",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5016", "51",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5016", "52",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1001", "5016", "53",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1005", "1005", "240802", ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
            { "1006", "1006", "240801", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR },
            { "1007", "4001", "41",     ExogenaMapeoCuentaEntity.MOVIMIENTO_CR },
            { "1007", "4002", "42",     ExogenaMapeoCuentaEntity.MOVIMIENTO_CR },
            { "1008", "1315", "1305",   ExogenaMapeoCuentaEntity.SALDO_DB },
            { "1009", "2201", "2205",   ExogenaMapeoCuentaEntity.SALDO_CR },
            { "2276", "2276", "5105",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB },
    };

    private static final Set<String> TIPOS_VALOR = Set.of(
            ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, ExogenaMapeoCuentaEntity.MOVIMIENTO_CR,
            ExogenaMapeoCuentaEntity.SALDO_DB, ExogenaMapeoCuentaEntity.SALDO_CR);

    private final ExogenaFormatoJPARepository formatoRepo;
    private final ExogenaConceptoJPARepository conceptoRepo;
    private final ExogenaMapeoCuentaJPARepository mapeoRepo;
    private final ExogenaLoteJPARepository loteRepo;
    private final ExogenaLineaJPARepository lineaRepo;
    private final ExogenaErrorJPARepository errorRepo;
    private final ExogenaQueryRepository queryRepo;

    // ── Catálogo ─────────────────────────────────────────────────────────

    public List<ExogenaFormatoEntity> formatos() {
        return formatoRepo.findByActivoTrueOrderByCodigoAsc();
    }

    public List<ExogenaConceptoEntity> conceptos(Long formatoId) {
        return conceptoRepo.findByFormatoIdOrderByCodigoAsc(formatoId);
    }

    // ── Mapeos por empresa ───────────────────────────────────────────────

    public List<ExogenaMapeoCuentaEntity> mapeos(Integer empresaId, Long formatoId) {
        if (formatoId == null) {
            return mapeoRepo.findByEmpresaId(empresaId);
        }
        List<Long> conceptoIds = conceptos(formatoId).stream()
                .map(ExogenaConceptoEntity::getId).toList();
        return conceptoIds.isEmpty() ? List.of()
                : mapeoRepo.findByEmpresaIdAndConceptoIdIn(empresaId, conceptoIds);
    }

    @Transactional
    public ExogenaMapeoCuentaEntity crearMapeo(Integer empresaId, Long conceptoId,
            String cuentaDesde, String cuentaHasta, String tipoValor) {
        if (conceptoId == null || conceptoRepo.findById(conceptoId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Concepto de exógena no encontrado");
        }
        if (cuentaDesde == null || cuentaDesde.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mapeo requiere la cuenta o prefijo inicial (cuentaDesde).");
        }
        if (tipoValor == null || !TIPOS_VALOR.contains(tipoValor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tipoValor inválido: use MOVIMIENTO_DB, MOVIMIENTO_CR, SALDO_DB o SALDO_CR.");
        }
        return mapeoRepo.save(ExogenaMapeoCuentaEntity.builder()
                .empresaId(empresaId)
                .conceptoId(conceptoId)
                .cuentaDesde(cuentaDesde.trim())
                .cuentaHasta(cuentaHasta != null && !cuentaHasta.isBlank() ? cuentaHasta.trim() : null)
                .tipoValor(tipoValor)
                .build());
    }

    @Transactional
    public void eliminarMapeo(Integer empresaId, Long mapeoId) {
        ExogenaMapeoCuentaEntity m = mapeoRepo.findByIdAndEmpresaId(mapeoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mapeo de exógena no encontrado"));
        mapeoRepo.delete(m);
    }

    /** Siembra los mapeos default (idempotente); se engancha a seedPUC. */
    @Transactional
    public void seedDefaults(Integer empresaId) {
        if (mapeoRepo.existsByEmpresaId(empresaId)) {
            return;
        }
        for (String[] d : MAPEOS_DEFAULT) {
            Optional<ExogenaFormatoEntity> formato = formatoRepo.findByCodigo(d[0]);
            if (formato.isEmpty()) {
                continue;   // catálogo aún no sembrado (V95 no aplicada)
            }
            conceptoRepo.findByFormatoIdOrderByCodigoAsc(formato.get().getId()).stream()
                    .filter(c -> c.getCodigo().equals(d[1]))
                    .findFirst()
                    .ifPresent(c -> mapeoRepo.save(ExogenaMapeoCuentaEntity.builder()
                            .empresaId(empresaId)
                            .conceptoId(c.getId())
                            .cuentaDesde(d[2])
                            .tipoValor(d[3])
                            .build()));
        }
    }

    // ── Validador previo ─────────────────────────────────────────────────

    /**
     * Corre las validaciones del prevalidador sin generar lote: períodos
     * abiertos, comprobantes en borrador, cuentas con movimiento sin mapeo
     * (de las clases que el formato cubre), terceros incompletos y
     * movimientos sin tercero en cuentas mapeadas.
     */
    public List<ExogenaErrorEntity> validar(Integer empresaId, int anio, Long formatoId) {
        List<ExogenaCalculadora.Mapeo> mapeos =
                ExogenaCalculadora.proyectar(mapeos(empresaId, formatoId));
        List<CuentaTerceroMovimientoDto> movimientos = queryRepo.movimientosDelAnio(empresaId, anio);

        List<ExogenaErrorEntity> errores = new ArrayList<>();
        for (Integer mes : queryRepo.mesesAbiertos(empresaId, anio)) {
            errores.add(error(empresaId, anio, ExogenaErrorEntity.PERIODO_ABIERTO,
                    "El período " + anio + "-" + String.format("%02d", mes)
                            + " sigue abierto: ciérrelo antes de generar la exógena.", null));
        }
        for (String comprobante : queryRepo.comprobantesBorrador(empresaId, anio)) {
            errores.add(error(empresaId, anio, ExogenaErrorEntity.COMPROBANTE_BORRADOR,
                    "Comprobante en borrador: " + comprobante, null));
        }

        // Clases PUC que el formato cubre (primer dígito de sus mapeos).
        Set<String> clasesDelFormato = mapeos.stream()
                .map(m -> m.desde().substring(0, 1)).collect(Collectors.toSet());
        Set<String> cuentasSinMapeo = new HashSet<>();
        Set<Long> tercerosMapeados = new HashSet<>();
        Set<String> cuentasSinTercero = new HashSet<>();
        for (CuentaTerceroMovimientoDto mov : movimientos) {
            boolean mapeada = ExogenaCalculadora.mapeoPara(mov.codigo(), mapeos).isPresent();
            if (mapeada) {
                if (mov.terceroId() != null) {
                    tercerosMapeados.add(mov.terceroId());
                } else if (mov.debito().subtract(mov.credito()).signum() != 0) {
                    cuentasSinTercero.add(mov.codigo());
                }
            } else if (clasesDelFormato.contains(mov.codigo().substring(0, 1))) {
                cuentasSinMapeo.add(mov.codigo());
            }
        }
        cuentasSinMapeo.stream().sorted().forEach(c ->
                errores.add(error(empresaId, anio, ExogenaErrorEntity.SIN_MAPEO,
                        "La cuenta " + c + " tiene movimiento y ninguna la cubre en el formato.", null)));
        cuentasSinTercero.stream().sorted().forEach(c ->
                errores.add(error(empresaId, anio, ExogenaErrorEntity.SIN_TERCERO,
                        "La cuenta " + c + " (mapeada) tiene movimientos sin tercero: irán a "
                                + "cuantías menores. Excluya el mapeo si no son pagos a terceros.", null)));

        for (TerceroExogenaDto t : queryRepo.terceros(empresaId, tercerosMapeados)) {
            List<String> faltantes = new ArrayList<>();
            if (esBlanco(t.numeroDocumento())) {
                faltantes.add("número de documento");
            }
            if ("NIT".equalsIgnoreCase(t.tipoDocumento()) && esBlanco(t.dv())) {
                faltantes.add("DV");
            }
            if (esBlanco(t.direccion())) {
                faltantes.add("dirección");
            }
            if (esBlanco(t.municipio())) {
                faltantes.add("municipio");
            }
            if (!faltantes.isEmpty()) {
                errores.add(error(empresaId, anio, ExogenaErrorEntity.TERCERO_INCOMPLETO,
                        t.nombreCompleto() + " (#" + t.id() + "): falta "
                                + String.join(", ", faltantes) + ".", t.id()));
            }
        }
        return errores;
    }

    // ── Generación de lotes ──────────────────────────────────────────────

    /**
     * Genera (o regenera) el lote del formato+año. Si la última versión está
     * en BORRADOR se regenera en el mismo lote; si está APROBADA se crea la
     * versión siguiente. Los hallazgos del validador se persisten con el lote.
     */
    @Transactional
    public ExogenaLoteEntity generar(Integer empresaId, Long usuarioId,
            Long formatoId, int anio, BigDecimal umbralCuantiaMenor) {
        ExogenaFormatoEntity formato = formatoRepo.findById(formatoId)
                .filter(ExogenaFormatoEntity::getActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Formato de exógena no encontrado"));
        List<ExogenaMapeoCuentaEntity> mapeosEntidad = mapeos(empresaId, formatoId);
        if (mapeosEntidad.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El formato " + formato.getCodigo() + " no tiene mapeos de cuenta "
                            + "configurados para esta empresa.");
        }
        BigDecimal umbral = umbralCuantiaMenor != null
                ? umbralCuantiaMenor : UMBRAL_CUANTIA_MENOR_DEFAULT;

        ExogenaLoteEntity lote = loteRepo
                .findFirstByEmpresaIdAndFormatoIdAndAnioOrderByVersionDesc(empresaId, formatoId, anio)
                .map(ultimo -> ExogenaLoteEntity.ESTADO_BORRADOR.equals(ultimo.getEstado())
                        ? reusar(ultimo, usuarioId, umbral)
                        : nuevaVersion(empresaId, formatoId, anio, ultimo.getVersion() + 1, usuarioId, umbral))
                .orElseGet(() -> nuevaVersion(empresaId, formatoId, anio, 1, usuarioId, umbral));

        List<ExogenaCalculadora.Mapeo> mapeos = ExogenaCalculadora.proyectar(mapeosEntidad);
        List<ExogenaCalculadora.Linea> lineas = ExogenaCalculadora.calcular(
                queryRepo.movimientosDelAnio(empresaId, anio),
                necesitaSaldos(mapeos) ? queryRepo.saldosAlCorte(empresaId, anio) : List.of(),
                mapeos, umbral);
        for (ExogenaCalculadora.Linea l : lineas) {
            lineaRepo.save(ExogenaLineaEntity.builder()
                    .loteId(lote.getId())
                    .conceptoId(l.conceptoId())
                    .terceroId(l.terceroId())
                    .valor(l.valor())
                    .cuantiaMenor(l.cuantiaMenor())
                    .build());
        }
        for (ExogenaErrorEntity e : validar(empresaId, anio, formatoId)) {
            e.setLoteId(lote.getId());
            errorRepo.save(e);
        }
        return lote;
    }

    public List<ExogenaLoteEntity> lotes(Integer empresaId, Integer anio) {
        return anio != null
                ? loteRepo.findByEmpresaIdAndAnioOrderByFormatoIdAscVersionDesc(empresaId, anio)
                : loteRepo.findByEmpresaIdOrderByAnioDescVersionDesc(empresaId);
    }

    public ExogenaLoteEntity lote(Integer empresaId, Long loteId) {
        return loteRepo.findByIdAndEmpresaId(loteId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote de exógena no encontrado"));
    }

    /** Líneas del lote enriquecidas con concepto y tercero para la pantalla. */
    public List<Map<String, Object>> lineas(Integer empresaId, Long loteId) {
        ExogenaLoteEntity lote = lote(empresaId, loteId);
        List<ExogenaLineaEntity> lineas = lineaRepo.findByLoteIdOrderByConceptoIdAscValorDesc(loteId);
        Map<Long, ExogenaConceptoEntity> conceptosPorId = conceptos(lote.getFormatoId()).stream()
                .collect(Collectors.toMap(ExogenaConceptoEntity::getId, Function.identity()));
        Map<Long, TerceroExogenaDto> tercerosPorId = queryRepo.terceros(empresaId,
                        lineas.stream().map(ExogenaLineaEntity::getTerceroId)
                                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TerceroExogenaDto::id, Function.identity()));

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (ExogenaLineaEntity l : lineas) {
            ExogenaConceptoEntity concepto = conceptosPorId.get(l.getConceptoId());
            TerceroExogenaDto tercero = l.getTerceroId() != null
                    ? tercerosPorId.get(l.getTerceroId()) : null;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", l.getId());
            fila.put("concepto", concepto != null ? concepto.getCodigo() : null);
            fila.put("conceptoNombre", concepto != null ? concepto.getNombre() : null);
            fila.put("terceroId", l.getTerceroId());
            fila.put("documento", tercero != null ? tercero.numeroDocumento()
                    : ExogenaExcelExporter.NIT_CUANTIAS_MENORES);
            fila.put("tercero", tercero != null ? tercero.nombreCompleto() : "CUANTÍAS MENORES");
            fila.put("valor", l.getValor());
            fila.put("cuantiaMenor", l.getCuantiaMenor());
            resultado.add(fila);
        }
        return resultado;
    }

    public List<ExogenaErrorEntity> errores(Integer empresaId, Long loteId) {
        lote(empresaId, loteId);
        return errorRepo.findByLoteIdOrderByTipoAscIdAsc(loteId);
    }

    /** Aprueba (bloquea) el lote: exige cero hallazgos del validador. */
    @Transactional
    public ExogenaLoteEntity aprobar(Integer empresaId, Long usuarioId, Long loteId) {
        ExogenaLoteEntity lote = lote(empresaId, loteId);
        if (!ExogenaLoteEntity.ESTADO_BORRADOR.equals(lote.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El lote ya está aprobado.");
        }
        long hallazgos = errorRepo.findByLoteIdOrderByTipoAscIdAsc(loteId).size();
        if (hallazgos > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El lote tiene " + hallazgos + " hallazgos del validador: corríjalos "
                            + "y regenere antes de aprobar.");
        }
        lote.setEstado(ExogenaLoteEntity.ESTADO_APROBADO);
        lote.setAprobadoPor(usuarioId);
        lote.setAprobadoEn(LocalDateTime.now());
        return loteRepo.save(lote);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ExogenaLoteEntity reusar(ExogenaLoteEntity lote, Long usuarioId, BigDecimal umbral) {
        lineaRepo.deleteByLoteId(lote.getId());
        errorRepo.deleteByLoteId(lote.getId());
        lote.setCuantiaMenorUmbral(umbral);
        lote.setGeneradoPor(usuarioId);
        lote.setGeneradoEn(LocalDateTime.now());
        return loteRepo.save(lote);
    }

    private ExogenaLoteEntity nuevaVersion(Integer empresaId, Long formatoId, int anio,
            int version, Long usuarioId, BigDecimal umbral) {
        return loteRepo.save(ExogenaLoteEntity.builder()
                .empresaId(empresaId)
                .formatoId(formatoId)
                .anio(anio)
                .version(version)
                .cuantiaMenorUmbral(umbral)
                .generadoPor(usuarioId)
                .build());
    }

    private static boolean necesitaSaldos(List<ExogenaCalculadora.Mapeo> mapeos) {
        return mapeos.stream().anyMatch(m -> m.tipoValor().startsWith("SALDO"));
    }

    private static ExogenaErrorEntity error(Integer empresaId, int anio, String tipo,
            String detalle, Long terceroId) {
        return ExogenaErrorEntity.builder()
                .empresaId(empresaId)
                .anio(anio)
                .tipo(tipo)
                .detalle(detalle)
                .terceroId(terceroId)
                .build();
    }

    private static boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }
}
