package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.auditoria.AreaAuditoria;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaResultadoDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDetalleDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDto;
import com.cloud_technological.aura_pos.dto.auditoria.Severidad;
import com.cloud_technological.aura_pos.repositories.auditoria.AuditoriaQueryRepository;
import com.cloud_technological.aura_pos.repositories.auditoria.AuditoriaQueryRepository.Resumen;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Motor de hallazgos del reporte gerencial (fase G1).
 *
 * <p>Corre los cruces de {@link AuditoriaQueryRepository} y los convierte en
 * hallazgos con severidad, monto y una recomendación. Es <b>solo el motor</b>:
 * devuelve JSON para poder validar los cruces contra datos reales antes de que
 * exista el PDF. Un hallazgo falso destruye la confianza en todo el documento,
 * así que primero se miran y después se maquetan.
 *
 * <p>La severidad la fija el tipo de desacuerdo, no la plata: un asiento
 * descuadrado de $1.000 es ALTA porque la contabilidad no cierra.
 */
@Slf4j
@Service
public class AuditoriaService {

    /** Diferencia tolerada en la ecuación contable: centavos de redondeo. */
    private static final BigDecimal TOLERANCIA_ECUACION = new BigDecimal("0.01");

    private final AuditoriaQueryRepository repository;
    private final AsientoContableService contabilidad;
    private final SecurityUtils securityUtils;

    public AuditoriaService(AuditoriaQueryRepository repository,
            AsientoContableService contabilidad, SecurityUtils securityUtils) {
        this.repository = repository;
        this.contabilidad = contabilidad;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public AuditoriaResultadoDto auditar(AuditoriaFiltroDto filtro) {
        long inicio = System.currentTimeMillis();
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate desde = validarDesde(filtro);
        LocalDate hasta = validarHasta(filtro, desde);

        boolean conDetalle = Boolean.TRUE.equals(filtro.getIncluirDetalle());
        int limite = filtro.getLimiteDetalle() != null ? filtro.getLimiteDetalle() : 100;
        // Debajo de este monto un descuadre de caja o de cartera es redondeo,
        // no un problema. La primera corrida real reportó un arqueo con 22
        // centavos de sobrante al lado de uno de $954.000: en la misma lista y
        // con el mismo peso, el de centavos le quita fuerza al que importa.
        BigDecimal umbral = filtro.getUmbralMonto() != null
                ? filtro.getUmbralMonto() : new BigDecimal("1000");

        AuditoriaResultadoDto out = new AuditoriaResultadoDto();
        out.setFechaDesde(desde);
        out.setFechaHasta(hasta);
        out.setGeneradoEn(LocalDateTime.now());

        List<HallazgoDto> hallazgos = new ArrayList<>();

        // ── Caja ──────────────────────────────────────────────────────
        agregar(hallazgos, construir(
                "CAJA_DIFERENCIA", "Arqueos que no cuadraron",
                "El efectivo contado al cerrar no coincidió con el que el sistema esperaba.",
                "Efectivo contado en el cierre  vs  base + ventas en efectivo + ingresos − egresos − comisiones",
                "Revisar turno por turno con el cajero. Un faltante repetido en la misma caja "
                        + "no es un error de conteo.",
                Severidad.ALTA, AreaAuditoria.CAJA, false,
                () -> repository.cajaDiferencias(empresaId, desde, hasta, umbral),
                conDetalle ? () -> repository.cajaDiferenciasDetalle(empresaId, desde, hasta, umbral, limite) : null));

        // Si hubo arqueos descuadrados, se mira qué pasó con el inventario en
        // esos mismos turnos: es la pista que convierte una cifra en una
        // hipótesis verificable.
        if (conDetalle) {
            adjuntarContextoDeInventario(hallazgos, empresaId, limite);
        }

        // ── Cartera ───────────────────────────────────────────────────
        agregar(hallazgos, construir(
                "CXC_SALDO_DESCUADRADO", "Cuentas por cobrar con el saldo desfasado",
                "El saldo guardado en la cuenta no coincide con lo que suman sus abonos. "
                        + "Es la cifra que el cliente va a reclamar.",
                "cuentas_cobrar.saldo_pendiente  vs  total_deuda − suma de abonos vivos",
                "Recalcular el saldo desde los abonos. Si la diferencia es grande, "
                        + "buscar abonos borrados o anulaciones que quedaron a medias.",
                Severidad.ALTA, AreaAuditoria.CARTERA, false,
                () -> repository.carteraDescuadre(empresaId, desde, hasta, true, umbral),
                conDetalle ? () -> repository.carteraDescuadreDetalle(empresaId, desde, hasta, true, umbral, limite) : null));

        agregar(hallazgos, construir(
                "CXP_SALDO_DESCUADRADO", "Cuentas por pagar con el saldo desfasado",
                "Lo mismo del lado del proveedor: lo que el sistema dice que se debe no "
                        + "coincide con lo que se le ha abonado.",
                "cuentas_pagar.saldo_pendiente  vs  total_deuda − suma de abonos vivos",
                "Recalcular el saldo desde los abonos antes de pagarle al proveedor.",
                Severidad.ALTA, AreaAuditoria.CARTERA, false,
                () -> repository.carteraDescuadre(empresaId, desde, hasta, false, umbral),
                conDetalle ? () -> repository.carteraDescuadreDetalle(empresaId, desde, hasta, false, umbral, limite) : null));

        // ── Inventario ────────────────────────────────────────────────
        agregar(hallazgos, construir(
                "KARDEX_CANTIDAD_INCOHERENTE", "Movimientos de inventario mal grabados",
                "La cantidad guardada en el movimiento no coincide con lo que cambió el saldo. "
                        + "Una de las dos cifras es falsa.",
                "movimiento_inventario.cantidad  vs  saldo_nuevo − saldo_anterior",
                "Revisar el documento que originó cada movimiento. El reporte de kardex usa "
                        + "la diferencia de saldos, así que sigue cuadrando; el riesgo es para "
                        + "quien lea la columna cantidad.",
                Severidad.MEDIA, AreaAuditoria.INVENTARIO, false,
                () -> repository.kardexSigno(empresaId, desde, hasta),
                conDetalle ? () -> repository.kardexSignoDetalle(empresaId, desde, hasta, limite) : null));

        agregar(hallazgos, construir(
                "STOCK_SIN_HISTORIA", "Stock que su propio kardex no explica",
                "El inventario dice una cantidad y el último movimiento registrado dice otra. "
                        + "Hay mercancía que entró o salió sin quedar registrada.",
                "inventario.stock_actual  vs  último saldo_nuevo del kardex",
                "Hacer un reconteo del producto. Si se repite en varios, buscar un proceso "
                        + "que esté tocando el stock sin escribir en el kardex.",
                Severidad.ALTA, AreaAuditoria.INVENTARIO, false,
                // Sin rango: el stock es acumulado, un descuadre viejo sigue mal hoy.
                () -> repository.kardexVsStock(empresaId),
                conDetalle ? () -> repository.kardexVsStockDetalle(empresaId, limite) : null));

        // ── Contabilidad ──────────────────────────────────────────────
        agregar(hallazgos, construir(
                "ASIENTO_DESCUADRADO", "Asientos que no cumplen partida doble",
                "Hay asientos donde el débito no es igual al crédito. La contabilidad no cierra.",
                "asiento_contable.total_debito  vs  total_credito",
                "Corregir antes de cerrar el período. Un solo asiento descuadrado invalida "
                        + "el balance completo.",
                Severidad.ALTA, AreaAuditoria.CONTABILIDAD, false,
                () -> repository.asientosDescuadrados(empresaId, desde, hasta),
                conDetalle ? () -> repository.asientosDescuadradosDetalle(empresaId, desde, hasta, limite) : null));

        agregar(hallazgos, construir(
                "ASIENTO_CABECERA_VS_LINEAS", "Asientos cuyos totales no suman sus líneas",
                "El total guardado en el asiento no coincide con la suma de sus movimientos. "
                        + "Los reportes que leen el total y los que leen las líneas dan distinto.",
                "asiento_contable.total_debito  vs  SUM(asiento_detalle.debito)",
                "Recalcular los totales desde las líneas.",
                Severidad.ALTA, AreaAuditoria.CONTABILIDAD, false,
                () -> repository.cabeceraVsDetalle(empresaId, desde, hasta),
                conDetalle ? () -> repository.cabeceraVsDetalleDetalle(empresaId, desde, hasta, limite) : null));

        agregar(hallazgos, ecuacionContable(empresaId, hasta));

        // ── Facturación ───────────────────────────────────────────────
        agregar(hallazgos, construir(
                "FACTURA_SIN_ACEPTAR", "Facturas electrónicas sin confirmación de la DIAN",
                "Se emitieron pero nunca se confirmó su aceptación. Frente a la DIAN es como "
                        + "si no existieran.",
                "factura.estado_dian = PENDIENTE dentro del período",
                "Reenviarlas o revisar por qué el envío no se completó.",
                Severidad.MEDIA, AreaAuditoria.FACTURACION, false,
                () -> repository.facturasSinAceptar(empresaId, desde, hasta),
                conDetalle ? () -> repository.facturasSinAceptarDetalle(empresaId, desde, hasta, limite) : null));

        // ── Gastos ────────────────────────────────────────────────────
        agregar(hallazgos, construir(
                "GASTO_DEDUCIBLE_SIN_SOPORTE", "Gastos marcados deducibles sin soporte",
                "Están marcados como deducibles pero no tienen documento soporte o no tienen "
                        + "tercero. En una revisión de renta no se aceptan.",
                "gasto.deducible = true  vs  numero_doc_soporte y tercero_id",
                "Conseguir el soporte o quitarles la marca de deducible. El beneficio "
                        + "tributario que el negocio cree tener sobre esta plata no existe.",
                Severidad.MEDIA, AreaAuditoria.GASTOS, false,
                () -> repository.gastosSinSoporte(empresaId, desde, hasta),
                conDetalle ? () -> repository.gastosSinSoporteDetalle(empresaId, desde, hasta, limite) : null));

        out.setHallazgos(hallazgos);

        // ── Heredados: van aparte, no se mezclan con la operación ─────
        if (Boolean.TRUE.equals(filtro.getIncluirHeredados())) {
            List<HallazgoDto> heredados = new ArrayList<>();

            agregar(heredados, construir(
                    "ABONO_CXC_SIN_ARQUEO", "Recaudos que no entraron a ningún arqueo",
                    "Abonos hechos desde comprobantes anteriores a la corrección: la plata "
                            + "entró pero no apareció en el cierre de ninguna caja.",
                    "abonos_cobrar sin turno_caja_id, sin caja_otro_dia y con método COMPROBANTE",
                    "No se corrigen solos: los arqueos de esos días ya están firmados. "
                            + "Sirven de inventario de lo que quedó fuera.",
                    Severidad.ALTA, AreaAuditoria.CAJA, true,
                    () -> repository.abonosSinArqueo(empresaId, desde, hasta, true),
                    conDetalle ? () -> repository.abonosSinArqueoDetalle(empresaId, desde, hasta, true, limite) : null));

            agregar(heredados, construir(
                    "ABONO_CXP_SIN_ARQUEO", "Pagos que no salieron de ningún arqueo",
                    "Lo mismo al revés: pagos a proveedor desde comprobantes viejos que no "
                            + "descontaron de ninguna caja.",
                    "abonos_pagar sin turno_caja_id, sin caja_otro_dia y con método COMPROBANTE",
                    "Inventario de lo que quedó fuera; no se reescriben arqueos cerrados.",
                    Severidad.ALTA, AreaAuditoria.CAJA, true,
                    () -> repository.abonosSinArqueo(empresaId, desde, hasta, false),
                    conDetalle ? () -> repository.abonosSinArqueoDetalle(empresaId, desde, hasta, false, limite) : null));

            agregar(heredados, construir(
                    "FACTURA_SIN_FECHA", "Facturas electrónicas sin fecha de emisión",
                    "Existen y se ven en el módulo, pero desaparecen de todo reporte que "
                            + "filtre por fecha — que son todos.",
                    "factura.fecha_hora_emision IS NULL",
                    "Rellenar la fecha desde created_at. Mientras tanto, el reporte de "
                            + "facturación electrónica sale incompleto.",
                    Severidad.ALTA, AreaAuditoria.FACTURACION, true,
                    // Sin rango: no tienen fecha con la cual acotarlas — ese es el hallazgo.
                    () -> repository.facturasSinFecha(empresaId),
                    conDetalle ? () -> repository.facturasSinFechaDetalle(empresaId, limite) : null));

            out.setHeredados(heredados);
        }

        contar(out);
        out.setDuracionMs(System.currentTimeMillis() - inicio);
        log.info("[Auditoría] Empresa {} — {} a {}: {} ALTA, {} MEDIA, {} BAJA en {} ms",
                empresaId, desde, hasta, out.getAlta(), out.getMedia(), out.getBaja(),
                out.getDuracionMs());
        return out;
    }

    /**
     * La ecuación contable: Activo = Pasivo + Patrimonio + Resultado.
     *
     * <p>Es el cruce más barato y el más grave. No cuenta filas — o cierra o no
     * cierra — así que se arma a mano en vez de pasar por un {@code Resumen}.
     *
     * <p>Se evalúa a la fecha final del período, no dentro del rango: el balance
     * es acumulado y preguntar "¿cerró en agosto?" no significa nada. La
     * pregunta es si cierra hoy.
     */
    private HallazgoDto ecuacionContable(Integer empresaId, LocalDate hasta) {
        BigDecimal diferencia;
        try {
            diferencia = contabilidad.balanceGeneral(empresaId, hasta.toString())
                    .getEcuacionContable();
        } catch (RuntimeException e) {
            // Sin plan de cuentas configurado el balance no se puede calcular.
            // Eso no es un hallazgo de auditoría: es que la contabilidad no se
            // está usando todavía.
            log.warn("[Auditoría] No se pudo calcular el balance: {}", e.getMessage());
            return new HallazgoDto();
        }
        if (diferencia == null || diferencia.abs().compareTo(TOLERANCIA_ECUACION) <= 0) {
            return new HallazgoDto();
        }

        HallazgoDto h = new HallazgoDto();
        h.setCodigo("ECUACION_DESCUADRADA");
        h.setTitulo("La ecuación contable no cierra");
        h.setDescripcion("Lo que el negocio tiene no coincide con lo que debe más lo que es "
                + "suyo. El balance no cuadra, así que ningún estado financiero que salga "
                + "de él es confiable.");
        h.setCruce("Activo  vs  Pasivo + Patrimonio + Resultado del ejercicio");
        h.setRecomendacion("Revisar los asientos descuadrados primero: suelen ser la causa. "
                + "Si no los hay, buscar cuentas mal clasificadas en el plan.");
        h.setSeveridad(Severidad.ALTA);
        h.setArea(AreaAuditoria.CONTABILIDAD);
        h.setCantidad(1);
        h.setMonto(diferencia.abs());
        h.setCausasProbables(causasDe("ECUACION_DESCUADRADA"));
        return h;
    }

    // ── Armado ────────────────────────────────────────────────────────

    /**
     * Corre el cruce y arma el hallazgo. El detalle solo se consulta si el
     * cruce encontró algo: preguntar por las filas de un hallazgo vacío es un
     * viaje a la base que siempre devuelve nada.
     */
    private HallazgoDto construir(String codigo, String titulo, String descripcion, String cruce,
            String recomendacion, Severidad severidad, AreaAuditoria area, boolean heredado,
            Supplier<Resumen> contar, Supplier<List<HallazgoDetalleDto>> detalle) {

        Resumen r = contar.get();
        HallazgoDto h = new HallazgoDto();
        h.setCodigo(codigo);
        h.setTitulo(titulo);
        h.setDescripcion(descripcion);
        h.setCruce(cruce);
        h.setRecomendacion(recomendacion);
        h.setSeveridad(severidad);
        h.setArea(area);
        h.setHeredado(heredado);
        h.setCausasProbables(causasDe(codigo));
        h.setCantidad(r.cantidad());
        h.setMonto(r.monto() != null ? r.monto() : BigDecimal.ZERO);
        // Cuando el cruce trae una magnitud física se guarda aparte: un
        // descuadre de inventario valorizado en $0 —porque al producto le falta
        // el costo— se lee como "no pasa nada" cuando hay unidades sin origen.
        if (r.unidades() != null && r.unidades().signum() != 0) {
            h.setMagnitud(r.unidades().stripTrailingZeros().toPlainString() + " unidades");
        }
        if (h.tieneAlgo() && detalle != null) {
            h.setDetalle(detalle.get());
        }
        return h;
    }

    /**
     * Por qué suele pasar cada cosa.
     *
     * <p>Esto es lo que convierte el reporte en un análisis: sin las causas, el
     * lector tiene una cifra y ninguna idea de dónde empezar a buscar. El orden
     * es por frecuencia real en este sistema, no por gravedad.
     *
     * <p>Las causas son <b>hipótesis</b>, no diagnósticos: el motor no puede
     * saber cuál fue. Por eso se listan varias y el texto del reporte las
     * presenta como lo que son.
     */
    private List<String> causasDe(String codigo) {
        return switch (codigo) {
            case "CAJA_DIFERENCIA" -> List.of(
                    "Ventas cobradas que no pasaron por el sistema: es la causa más "
                            + "común de un sobrante grande, y la más costosa de ignorar.",
                    "Gastos o retiros pagados del cajón sin registrarlos — produce faltante.",
                    "La base inicial declarada al abrir no era la que realmente había.",
                    "Vueltos mal entregados o conteo apurado al cierre: explican "
                            + "diferencias pequeñas, no las grandes.",
                    "Una venta anulada después de haberla cobrado.");

            case "CXC_SALDO_DESCUADRADO", "CXP_SALDO_DESCUADRADO" -> List.of(
                    "Un abono eliminado directamente en la base, sin pasar por el "
                            + "sistema: el saldo guardado nunca se enteró.",
                    "Una anulación que quedó a medias — se revirtió el documento "
                            + "pero no el abono, o al revés.",
                    "Un abono registrado dos veces y borrado una sola.",
                    "Migración de saldos iniciales cargados sin sus abonos.");

            case "STOCK_SIN_HISTORIA" -> List.of(
                    "Un ajuste de stock hecho directamente sobre la tabla de "
                            + "inventario, sin escribir el movimiento en el kardex.",
                    "El producto se creó con existencia inicial y esa carga no "
                            + "generó movimiento: es lo típico en productos migrados.",
                    "Un traslado entre sucursales que registró la salida pero no la "
                            + "entrada, o al revés.",
                    "Un proceso que descuenta stock sin pasar por el kardex — si se "
                            + "repite en muchos productos, la causa es esta.");

            case "KARDEX_CANTIDAD_INCOHERENTE" -> List.of(
                    "El documento de origen guardó la cantidad con el signo cambiado.",
                    "El saldo anterior se leyó después de que otro proceso ya lo "
                            + "había movido: dos operaciones sobre el mismo producto "
                            + "al mismo tiempo.",
                    "Una edición del documento que corrigió el stock pero no la fila "
                            + "del kardex.");

            case "ASIENTO_DESCUADRADO" -> List.of(
                    "Un comprobante manual que se guardó sin cuadrar débito contra "
                            + "crédito.",
                    "Líneas del asiento editadas después de crearlo, sin recalcular "
                            + "los totales de la cabecera.",
                    "Un asiento automático cuya parametrización de cuentas está "
                            + "incompleta: falta la contrapartida.");

            case "ASIENTO_CABECERA_VS_LINEAS" -> List.of(
                    "Se agregaron o quitaron líneas después de guardar el asiento.",
                    "Un proceso escribió las líneas sin actualizar los totales.");

            case "ECUACION_DESCUADRADA" -> List.of(
                    "Asientos descuadrados dentro del período: revíselos primero, "
                            + "suelen ser la causa completa.",
                    "Cuentas mal clasificadas en el plan — un gasto colgado bajo "
                            + "activo, por ejemplo.",
                    "Saldos iniciales cargados sin su contrapartida de patrimonio.");

            case "FACTURA_SIN_ACEPTAR" -> List.of(
                    "El envío a la DIAN falló y nadie lo reintentó.",
                    "Credenciales o resolución de facturación vencidas.",
                    "La factura se emitió en ambiente de pruebas por configuración.");

            case "GASTO_DEDUCIBLE_SIN_SOPORTE" -> List.of(
                    "Se registró el gasto sin el documento a la mano y nunca se "
                            + "volvió a completar.",
                    "El proveedor no entregó factura: entonces el gasto no es "
                            + "deducible y la marca está mal puesta.",
                    "Se marcó deducible por defecto sin revisar.");

            case "ABONO_CXC_SIN_ARQUEO", "ABONO_CXP_SIN_ARQUEO" -> List.of(
                    "Comprobantes manuales anteriores a la corrección: no preguntaban "
                            + "de dónde salía la plata, así que el abono nacía sin caja.",
                    "No hay nada que corregir en el pasado: los arqueos de esos días "
                            + "ya están firmados. Sirve como inventario de lo que quedó "
                            + "fuera.");

            case "FACTURA_SIN_FECHA" -> List.of(
                    "Facturas cargadas por migración sin la fecha de emisión.",
                    "Un flujo de creación que no llenaba el campo.");

            default -> List.of();
        };
    }

    /**
     * Le cuelga al hallazgo de caja lo que se movió en el inventario durante
     * esos turnos.
     *
     * <p>Un sobrante grande y una merma del mismo día en la misma sucursal no
     * son dos hallazgos: son uno con dos caras. Mostrarlos juntos es la
     * diferencia entre "revise la caja" y "revise esta merma contra este
     * sobrante".
     */
    private void adjuntarContextoDeInventario(List<HallazgoDto> hallazgos, Integer empresaId,
            int limite) {
        hallazgos.stream()
                .filter(h -> "CAJA_DIFERENCIA".equals(h.getCodigo()))
                .findFirst()
                .ifPresent(h -> {
                    List<Long> turnos = h.getDetalle().stream()
                            .map(HallazgoDetalleDto::getOrigenId)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    List<HallazgoDetalleDto> ctx =
                            repository.movimientosDuranteTurnos(empresaId, turnos, limite);
                    if (!ctx.isEmpty()) {
                        h.setContexto(ctx);
                        h.setContextoTitulo("Qué se movió en el inventario durante esos turnos "
                                + "(sin contar ventas ni compras)");
                    }
                });
    }

    /** Un hallazgo sin filas no se reporta: no hay nada que mirar. */
    private void agregar(List<HallazgoDto> destino, HallazgoDto h) {
        if (h.tieneAlgo()) {
            destino.add(h);
        }
    }

    /**
     * El semáforo.
     *
     * <p>El monto en riesgo suma solo los ALTA. Mezclar las tres severidades
     * daría una cifra que no significa nada: un gasto sin soporte y un asiento
     * descuadrado no son la misma clase de problema.
     */
    private void contar(AuditoriaResultadoDto out) {
        List<HallazgoDto> todos = new ArrayList<>(out.getHallazgos());
        todos.addAll(out.getHeredados());

        BiFunction<List<HallazgoDto>, Severidad, Integer> cuantos =
                (lista, sev) -> (int) lista.stream().filter(h -> h.getSeveridad() == sev).count();

        out.setAlta(cuantos.apply(todos, Severidad.ALTA));
        out.setMedia(cuantos.apply(todos, Severidad.MEDIA));
        out.setBaja(cuantos.apply(todos, Severidad.BAJA));

        out.setMontoEnRiesgo(todos.stream()
                .filter(h -> h.getSeveridad() == Severidad.ALTA)
                .map(HallazgoDto::getMonto)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ── Validación ────────────────────────────────────────────────────

    /**
     * Sin fecha inicial se toma el mes corriente.
     *
     * <p>No se permite un rango abierto: los cruces recorren las tablas más
     * grandes del sistema y "toda la historia" es una consulta que nadie va a
     * esperar. El límite de 92 días es deliberado — un trimestre es lo máximo
     * que se audita de una sentada.
     */
    private LocalDate validarDesde(AuditoriaFiltroDto f) {
        return f.getFechaDesde() != null ? f.getFechaDesde() : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate validarHasta(AuditoriaFiltroDto f, LocalDate desde) {
        LocalDate hasta = f.getFechaHasta() != null ? f.getFechaHasta() : LocalDate.now();
        if (hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la inicial.");
        }
        if (desde.plusDays(92).isBefore(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La auditoría se corre sobre máximo un trimestre. "
                            + "Consulte por períodos más cortos.");
        }
        return hasta;
    }
}
