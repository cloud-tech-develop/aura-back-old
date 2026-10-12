package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.entity.ExogenaColumnaEntity;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;
import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaErrorEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLineaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaColumnaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaConceptoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaErrorJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaFormatoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaLoteJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaMapeoCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaQueryRepository.PagoNomina;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Información exógena DIAN (E11 · C7): mapeos cuenta→concepto por empresa,
 * validador previo (terceros incompletos, cuentas sin mapeo, borradores,
 * períodos abiertos), generación de lotes versionados con cuantías menores y
 * aprobación que bloquea el lote. La aritmética vive en
 * {@link ExogenaCalculadora}; el Excel del prevalidador en
 * {@link ExogenaExcelExporter}.
 *
 * <p>V225: cada formato tiene varias columnas de valor y su fuente (contabilidad o,
 * para el 2276, la nómina); los formatos se filtran por el tipo de persona del
 * informante (el 1010 de socios solo para sociedades).
 */
@Service
@RequiredArgsConstructor
public class ExogenaService {

    public static final BigDecimal UMBRAL_CUANTIA_MENOR_DEFAULT = new BigDecimal("100000");

    /**
     * Mapeos default sobre el PUC seed: formato, concepto, prefijo, tipo de valor y
     * columna (null = la principal del formato).
     */
    private static final String[][] MAPEOS_DEFAULT = {
            { "1001", "5001", "5105",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5006", "5305",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5007", "1435",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5008", "15",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5016", "51",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5016", "52",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1001", "5016", "53",     ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            // Retenciones practicadas: cada subcuenta de la 2365 a su concepto.
            { "1001", "5001", "236505", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5002", "236515", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5003", "236520", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5004", "236525", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5005", "236530", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5006", "236535", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5007", "236540", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5016", "2365",   ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_RENTA" },
            { "1001", "5016", "2367",   ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "RET_IVA_RC" },
            // Retenciones que le practicaron (anticipo de impuestos) y su base (ingresos).
            { "1003", "1303", "135515", ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, "RETENCION" },
            { "1003", "1309", "135517", ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, "RETENCION" },
            { "1003", "1303", "41",     ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, "BASE" },
            { "1005", "1005", "240802", ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, null },
            { "1006", "1006", "240801", ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, null },
            { "1007", "4001", "41",     ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, null },
            { "1007", "4002", "42",     ExogenaMapeoCuentaEntity.MOVIMIENTO_CR, null },
            { "1007", "4001", "4175",   ExogenaMapeoCuentaEntity.MOVIMIENTO_DB, "DEVOLUCIONES" },
            { "1008", "1315", "1305",   ExogenaMapeoCuentaEntity.SALDO_DB, null },
            { "1009", "2201", "2205",   ExogenaMapeoCuentaEntity.SALDO_CR, null },
            { "1010", "1010", "3105",   ExogenaMapeoCuentaEntity.SALDO_CR, "VALOR_PATRIMONIAL" },
            { "1010", "1010", "3115",   ExogenaMapeoCuentaEntity.SALDO_CR, "VALOR_PATRIMONIAL" },
            { "1012", "1110", "1110",   ExogenaMapeoCuentaEntity.SALDO_DB, "VALOR" },
            { "1012", "1110", "1120",   ExogenaMapeoCuentaEntity.SALDO_DB, "VALOR" },
            { "1012", "1115", "1225",   ExogenaMapeoCuentaEntity.SALDO_DB, "VALOR" },
    };

    /** Formatos con reglas propias. */
    private static final String F_SOCIOS = "1010";
    private static final String F_BANCOS = "1012";

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
    private final ExogenaColumnaJPARepository columnaRepo;
    private final ObjectMapper json = new ObjectMapper();

    // ── Catálogo ─────────────────────────────────────────────────────────

    public List<ExogenaFormatoEntity> formatos() {
        return formatoRepo.findByActivoTrueOrderByCodigoAsc();
    }

    /**
     * Formatos que reporta la empresa según su tipo de persona: una persona natural
     * no reporta el 1010 (socios).
     */
    public List<ExogenaFormatoEntity> formatos(Integer empresaId) {
        boolean juridica = !"NATURAL".equals(queryRepo.tipoPersonaEmpresa(empresaId));
        return formatos().stream()
                .filter(f -> juridica || !ExogenaFormatoEntity.APLICA_JURIDICA.equals(f.getAplicaA()))
                .toList();
    }

    /** Columnas de valor del formato en el orden del prevalidador. */
    public List<ExogenaColumnaEntity> columnas(Long formatoId) {
        return columnaRepo.findByFormatoIdOrderByOrdenAsc(formatoId);
    }

    /** Tipo de persona del informante (NATURAL | JURIDICA). */
    public String tipoPersona(Integer empresaId) {
        return queryRepo.tipoPersonaEmpresa(empresaId);
    }

    @Transactional
    public String cambiarTipoPersona(Integer empresaId, String tipoPersona) {
        if (!"NATURAL".equals(tipoPersona) && !"JURIDICA".equals(tipoPersona))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de persona inválido: NATURAL o JURIDICA.");
        queryRepo.cambiarTipoPersonaEmpresa(empresaId, tipoPersona);
        return tipoPersona;
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
            String cuentaDesde, String cuentaHasta, String tipoValor, String columna) {
        ExogenaConceptoEntity concepto = conceptoId == null ? null : conceptoRepo.findById(conceptoId).orElse(null);
        if (concepto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Concepto de exógena no encontrado");
        }
        if (columna != null && !columna.isBlank()
                && columnas(concepto.getFormatoId()).stream().noneMatch(c -> c.getCodigo().equals(columna))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La columna " + columna + " no existe en el formato.");
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
                .columna(columna != null && !columna.isBlank() ? columna : null)
                .build());
    }

    @Transactional
    public void eliminarMapeo(Integer empresaId, Long mapeoId) {
        ExogenaMapeoCuentaEntity m = mapeoRepo.findByIdAndEmpresaId(mapeoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mapeo de exógena no encontrado"));
        mapeoRepo.delete(m);
    }

    /**
     * Siembra los mapeos default que falten (idempotente; se engancha a seedPUC y a
     * "Restaurar defaults"). No toca los que la empresa ya tenga ni borra los suyos.
     */
    @Transactional
    public void seedDefaults(Integer empresaId) {
        List<ExogenaMapeoCuentaEntity> actuales = mapeoRepo.findByEmpresaId(empresaId);
        for (String[] d : MAPEOS_DEFAULT) {
            Optional<ExogenaFormatoEntity> formato = formatoRepo.findByCodigo(d[0]);
            if (formato.isEmpty()) {
                continue;   // catálogo aún no sembrado
            }
            conceptoRepo.findByFormatoIdOrderByCodigoAsc(formato.get().getId()).stream()
                    .filter(c -> c.getCodigo().equals(d[1]))
                    .findFirst()
                    .filter(c -> actuales.stream().noneMatch(m -> m.getConceptoId().equals(c.getId())
                            && m.getCuentaDesde().equals(d[2])
                            && Objects.equals(m.getColumna(), d[4])))
                    .ifPresent(c -> mapeoRepo.save(ExogenaMapeoCuentaEntity.builder()
                            .empresaId(empresaId)
                            .conceptoId(c.getId())
                            .cuentaDesde(d[2])
                            .tipoValor(d[3])
                            .columna(d[4])
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
        ExogenaFormatoEntity formato = formatoRepo.findById(formatoId).orElse(null);
        if (formato != null && ExogenaFormatoEntity.FUENTE_NOMINA.equals(formato.getFuente())) {
            return validarNomina(empresaId, anio, formato);
        }
        List<ExogenaCalculadora.Mapeo> mapeos =
                ExogenaCalculadora.proyectar(mapeos(empresaId, formatoId));
        List<CuentaTerceroMovimientoDto> movimientos = conBanco(formato, empresaId,
                queryRepo.movimientosDelAnio(empresaId, anio));

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

        errores.addAll(tercerosIncompletos(empresaId, anio, formato, tercerosMapeados));
        errores.addAll(informanteIncompleto(empresaId, anio, formato));
        return errores;
    }

    /**
     * La fila de cuantías menores lleva la dirección y el municipio del informante: si
     * la empresa no los tiene, el prevalidador la rechaza.
     */
    private List<ExogenaErrorEntity> informanteIncompleto(Integer empresaId, int anio, ExogenaFormatoEntity formato) {
        if (formato == null || formato.getEncabezado() == null || !formato.getEncabezado().contains("DIR")
                || formato.getCuantiaMenor() == null || formato.getCuantiaMenor().signum() == 0)
            return List.of();
        ExogenaExcelExporter.Informante inf = queryRepo.informante(empresaId);
        List<String> faltantes = new ArrayList<>();
        if (esBlanco(inf.direccion())) faltantes.add("la dirección de la sede");
        if (esBlanco(inf.codigoDane())) faltantes.add("el municipio de la empresa");
        if (faltantes.isEmpty()) return List.of();
        return List.of(error(empresaId, anio, "INFORMANTE_INCOMPLETO",
                "A la empresa le falta " + String.join(" y ", faltantes)
                        + ": la fila de cuantías menores lleva la ubicación del informante.", null));
    }

    /**
     * Terceros a los que les falta lo que pide el encabezado del formato: documento,
     * nombre o razón social y, si el formato lleva ubicación, dirección y municipio.
     * El DV no se exige: si falta se calcula al exportar.
     */
    private List<ExogenaErrorEntity> tercerosIncompletos(Integer empresaId, int anio, ExogenaFormatoEntity formato,
            Set<Long> ids) {
        String enc = formato != null && formato.getEncabezado() != null ? formato.getEncabezado() : "DIR,MUN";
        List<ExogenaErrorEntity> errores = new ArrayList<>();
        for (TerceroExogenaDto t : queryRepo.terceros(empresaId, ids)) {
            List<String> faltantes = new ArrayList<>();
            if (esBlanco(t.numeroDocumento())) faltantes.add("número de documento");
            if (esBlanco(t.razonSocial()) && esBlanco(t.nombre1()) && esBlanco(t.nombres()))
                faltantes.add("nombre o razón social");
            if (enc.contains("DIR") && esBlanco(t.direccion())) faltantes.add("dirección");
            if (enc.contains("MUN") && esBlanco(t.codigoDane())) faltantes.add("municipio");
            if (!faltantes.isEmpty()) {
                errores.add(error(empresaId, anio, ExogenaErrorEntity.TERCERO_INCOMPLETO,
                        t.nombreCompleto() + " (#" + t.id() + "): falta "
                                + String.join(", ", faltantes) + ".", t.id()));
            }
        }
        return errores;
    }

    /** 2276: empleados sin tercero (no se pueden reportar) y terceros incompletos. */
    private List<ExogenaErrorEntity> validarNomina(Integer empresaId, int anio, ExogenaFormatoEntity formato) {
        List<ExogenaErrorEntity> errores = new ArrayList<>();
        Set<Long> terceros = new HashSet<>();
        Set<Long> sinTercero = new HashSet<>();
        for (PagoNomina p : queryRepo.pagosNominaDelAnio(empresaId, anio)) {
            if (p.terceroId() != null) terceros.add(p.terceroId());
            else if (sinTercero.add(p.empleadoId()))
                errores.add(error(empresaId, anio, ExogenaErrorEntity.SIN_TERCERO,
                        "El empleado " + p.empleadoNombre() + " no tiene tercero: complete su hoja de vida.", null));
        }
        errores.addAll(tercerosIncompletos(empresaId, anio, formato, terceros));
        return errores;
    }

    /**
     * 1012: los saldos de bancos suelen contabilizarse sin tercero; se les pone el
     * banco de la cuenta bancaria que usa esa cuenta contable.
     */
    private List<CuentaTerceroMovimientoDto> conBanco(ExogenaFormatoEntity formato, Integer empresaId,
            List<CuentaTerceroMovimientoDto> movimientos) {
        if (formato == null || !F_BANCOS.equals(formato.getCodigo())) return movimientos;
        Map<String, Long> bancos = queryRepo.bancoPorCuentaContable(empresaId);
        return movimientos.stream()
                .map(m -> m.terceroId() == null && bancos.containsKey(m.codigo())
                        ? new CuentaTerceroMovimientoDto(m.codigo(), bancos.get(m.codigo()), m.debito(), m.credito())
                        : m)
                .toList();
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
        boolean deNomina = ExogenaFormatoEntity.FUENTE_NOMINA.equals(formato.getFuente());
        List<ExogenaMapeoCuentaEntity> mapeosEntidad = mapeos(empresaId, formatoId);
        if (!deNomina && mapeosEntidad.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El formato " + formato.getCodigo() + " no tiene mapeos de cuenta "
                            + "configurados para esta empresa.");
        }
        BigDecimal umbral = umbralCuantiaMenor != null ? umbralCuantiaMenor
                : formato.getCuantiaMenor() != null ? formato.getCuantiaMenor() : UMBRAL_CUANTIA_MENOR_DEFAULT;

        ExogenaLoteEntity lote = loteRepo
                .findFirstByEmpresaIdAndFormatoIdAndAnioOrderByVersionDesc(empresaId, formatoId, anio)
                .map(ultimo -> ExogenaLoteEntity.ESTADO_BORRADOR.equals(ultimo.getEstado())
                        ? reusar(ultimo, usuarioId, umbral)
                        : nuevaVersion(empresaId, formatoId, anio, ultimo.getVersion() + 1, usuarioId, umbral))
                .orElseGet(() -> nuevaVersion(empresaId, formatoId, anio, 1, usuarioId, umbral));

        String principal = columnas(formatoId).stream().filter(ExogenaColumnaEntity::getPrincipal)
                .map(ExogenaColumnaEntity::getCodigo).findFirst().orElse(ExogenaCalculadora.VALOR);
        List<ExogenaCalculadora.Linea> lineas;
        if (deNomina) {
            lineas = lineas2276(empresaId, anio, formatoId, principal);
        } else {
            List<ExogenaCalculadora.Mapeo> mapeos = ExogenaCalculadora.proyectar(mapeosEntidad);
            lineas = ExogenaCalculadora.calcular(
                    conBanco(formato, empresaId, queryRepo.movimientosDelAnio(empresaId, anio)),
                    necesitaSaldos(mapeos) ? conBanco(formato, empresaId, queryRepo.saldosAlCorte(empresaId, anio))
                            : List.of(),
                    mapeos, new ExogenaCalculadora.Reglas(principal, formato.getColumnaRequerida(), umbral));
            if (F_SOCIOS.equals(formato.getCodigo()))
                lineas = ExogenaCalculadora.conPorcentaje(lineas, "VALOR_PATRIMONIAL", "PORCENTAJE");
        }
        for (ExogenaCalculadora.Linea l : lineas) {
            lineaRepo.save(ExogenaLineaEntity.builder()
                    .loteId(lote.getId())
                    .conceptoId(l.conceptoId())
                    .terceroId(l.terceroId())
                    .valor(l.valor())
                    .valores(aJson(l.valores()))
                    .cuantiaMenor(l.cuantiaMenor())
                    .build());
        }
        for (ExogenaErrorEntity e : validar(empresaId, anio, formatoId)) {
            e.setLoteId(lote.getId());
            errorRepo.save(e);
        }
        return lote;
    }

    /**
     * 2276: una línea por empleado (su tercero) con lo pagado en la nómina del año,
     * clasificado por columna.
     */
    private List<ExogenaCalculadora.Linea> lineas2276(Integer empresaId, int anio, Long formatoId, String principal) {
        Long conceptoId = conceptos(formatoId).stream().findFirst().map(ExogenaConceptoEntity::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El formato 2276 no tiene concepto en el catálogo."));
        Map<Long, List<Clasificador2276.Pago>> porTercero = new LinkedHashMap<>();
        for (PagoNomina p : queryRepo.pagosNominaDelAnio(empresaId, anio))
            if (p.terceroId() != null)
                porTercero.computeIfAbsent(p.terceroId(), k -> new ArrayList<>()).add(p.pago());
        List<ExogenaCalculadora.Linea> lineas = new ArrayList<>();
        porTercero.forEach((terceroId, pagos) -> {
            Map<String, BigDecimal> valores = Clasificador2276.valores(pagos);
            if (valores.values().stream().anyMatch(v -> v.signum() != 0))
                lineas.add(new ExogenaCalculadora.Linea(conceptoId, terceroId, valores, false, principal));
        });
        return lineas;
    }

    /**
     * Genera de una vez los lotes del año de todos los formatos que reporta la
     * empresa. Los de contabilidad sin mapeos se saltan con su motivo.
     *
     * @return por formato: lote generado, líneas y hallazgos, o el motivo si no se generó
     */
    @Transactional
    public List<Map<String, Object>> generarTodos(Integer empresaId, Long usuarioId, int anio) {
        List<Map<String, Object>> resumen = new ArrayList<>();
        for (ExogenaFormatoEntity f : formatos(empresaId)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("formato", f.getCodigo());
            r.put("nombre", f.getNombre());
            boolean deNomina = ExogenaFormatoEntity.FUENTE_NOMINA.equals(f.getFuente());
            if (!deNomina && mapeos(empresaId, f.getId()).isEmpty()) {
                r.put("generado", false);
                r.put("motivo", "Sin mapeos de cuenta configurados.");
                resumen.add(r);
                continue;
            }
            ExogenaLoteEntity lote = generar(empresaId, usuarioId, f.getId(), anio, null);
            r.put("generado", true);
            r.put("loteId", lote.getId());
            r.put("version", lote.getVersion());
            r.put("lineas", lineaRepo.findByLoteIdOrderByConceptoIdAscValorDesc(lote.getId()).size());
            r.put("hallazgos", errorRepo.findByLoteIdOrderByTipoAscIdAsc(lote.getId()).size());
            resumen.add(r);
        }
        return resumen;
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
                                .filter(Objects::nonNull).collect(Collectors.toSet()))
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
            fila.put("valores", deJson(l.getValores(), l.getValor()));
            fila.put("cuantiaMenor", l.getCuantiaMenor());
            resultado.add(fila);
        }
        return resultado;
    }

    /** Hoja de Excel de un lote: filas con su tercero y todos sus valores. */
    public ExogenaExcelExporter.Hoja hoja(Integer empresaId, Long loteId) {
        ExogenaLoteEntity lote = lote(empresaId, loteId);
        ExogenaFormatoEntity formato = formatoRepo.findById(lote.getFormatoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formato no encontrado"));
        List<ExogenaLineaEntity> lineas = lineaRepo.findByLoteIdOrderByConceptoIdAscValorDesc(loteId);
        Map<Long, ExogenaConceptoEntity> conceptosPorId = conceptos(lote.getFormatoId()).stream()
                .collect(Collectors.toMap(ExogenaConceptoEntity::getId, Function.identity()));
        Map<Long, TerceroExogenaDto> tercerosPorId = queryRepo.terceros(empresaId,
                        lineas.stream().map(ExogenaLineaEntity::getTerceroId)
                                .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TerceroExogenaDto::id, Function.identity()));
        List<ExogenaExcelExporter.Fila> filas = lineas.stream()
                .map(l -> new ExogenaExcelExporter.Fila(
                        conceptosPorId.containsKey(l.getConceptoId()) ? conceptosPorId.get(l.getConceptoId()).getCodigo()
                                : null,
                        l.getTerceroId() != null ? tercerosPorId.get(l.getTerceroId()) : null,
                        deJson(l.getValores(), l.getValor())))
                .toList();
        return new ExogenaExcelExporter.Hoja(formato, columnas(formato.getId()), lote, filas,
                queryRepo.informante(empresaId));
    }

    /** El año completo: la última versión del lote de cada formato que tenga. */
    public List<ExogenaExcelExporter.Hoja> hojasDelAnio(Integer empresaId, int anio) {
        List<ExogenaExcelExporter.Hoja> hojas = new ArrayList<>();
        for (ExogenaFormatoEntity f : formatos(empresaId))
            loteRepo.findFirstByEmpresaIdAndFormatoIdAndAnioOrderByVersionDesc(empresaId, f.getId(), anio)
                    .ifPresent(l -> hojas.add(hoja(empresaId, l.getId())));
        return hojas;
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

    private String aJson(Map<String, BigDecimal> valores) {
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar los valores de la línea", e);
        }
    }

    /** Valores de la línea; las líneas viejas (sin JSON) solo tienen la columna principal. */
    public Map<String, BigDecimal> deJson(String valores, BigDecimal valor) {
        if (valores == null || valores.isBlank()) {
            Map<String, BigDecimal> v = new LinkedHashMap<>();
            v.put(ExogenaCalculadora.VALOR, valor);
            return v;
        }
        try {
            return json.readValue(valores, new TypeReference<LinkedHashMap<String, BigDecimal>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Valores de la línea de exógena ilegibles", e);
        }
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
