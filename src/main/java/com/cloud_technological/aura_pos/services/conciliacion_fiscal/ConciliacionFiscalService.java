package com.cloud_technological.aura_pos.services.conciliacion_fiscal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Ajuste;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cabecera;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Crear;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.FilaBalance;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Resumen;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.ResultadoFila;
import com.cloud_technological.aura_pos.entity.ConciliacionFiscalAjusteEntity;
import com.cloud_technological.aura_pos.entity.ConciliacionFiscalCuentaEntity;
import com.cloud_technological.aura_pos.entity.ConciliacionFiscalEntity;
import com.cloud_technological.aura_pos.repositories.conciliacion_fiscal.ConciliacionFiscalAjusteJPARepository;
import com.cloud_technological.aura_pos.repositories.conciliacion_fiscal.ConciliacionFiscalCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.conciliacion_fiscal.ConciliacionFiscalJPARepository;
import com.cloud_technological.aura_pos.repositories.conciliacion_fiscal.ConciliacionFiscalQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;
import java.math.RoundingMode;

/**
 * Conciliación fiscal 2516 / 2517 (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §5).
 *
 * <p>El balance del año se sube en Excel (el de cualquier software) o se trae de la
 * contabilidad de Aura; cada cuenta cae en un renglón del formato y el contador
 * registra los ajustes fiscales. El análisis (cuadre, cuentas sin clasificar,
 * ajustes sugeridos, impuesto diferido, renta líquida) lo hace
 * {@link AnalizadorConciliacion} cada vez que se consulta. No genera asientos.
 */
@Service
@RequiredArgsConstructor
public class ConciliacionFiscalService {

    static final Set<String> FORMATOS = Set.of("2516", "2517");

    private final ConciliacionFiscalJPARepository conciliacionRepo;
    private final ConciliacionFiscalCuentaJPARepository cuentaRepo;
    private final ConciliacionFiscalAjusteJPARepository ajusteRepo;
    private final ConciliacionFiscalQueryRepository query;

    public List<Cabecera> listar(Integer empresaId) {
        return query.listar(empresaId);
    }

    @Transactional
    public Cabecera crear(Integer empresaId, Integer usuarioId, Crear dto) {
        int anioActual = LocalDate.now().getYear();
        if (dto.getAnio() == null || dto.getAnio() < 2015 || dto.getAnio() > anioActual) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija un año gravable entre 2015 y " + anioActual + ".");
        }
        if (dto.getFormato() == null || !FORMATOS.contains(dto.getFormato())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el formato: 2516 (personas jurídicas) o 2517 (personas naturales).");
        }
        BigDecimal tarifa = dto.getTarifaRenta() != null ? dto.getTarifaRenta() : new BigDecimal("35");
        validarTarifa(tarifa);
        if (query.existe(empresaId, dto.getAnio(), dto.getFormato())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Ya existe la conciliación " + dto.getFormato() + " del año " + dto.getAnio() + ".");
        }
        ConciliacionFiscalEntity c = conciliacionRepo.save(ConciliacionFiscalEntity.builder()
                .empresaId(empresaId)
                .anio(dto.getAnio())
                .formato(dto.getFormato())
                .tarifaRenta(tarifa)
                .estado(ConciliacionFiscalEntity.ESTADO_BORRADOR)
                .usuarioId(usuarioId)
                .build());
        return query.cabecera(c.getId(), empresaId);
    }

    public Resumen obtener(Integer empresaId, Long id) {
        Cabecera cab = query.cabecera(id, empresaId);
        if (cab == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Conciliación no encontrada");
        List<Cuenta> cuentas = query.cuentas(id);
        cuentas.forEach(c -> {
            var def = c.getRenglon() != null ? RenglonesFiscales.POR_CODIGO.get(c.getRenglon()) : null;
            c.setRenglonNombre(def != null ? def.nombre()
                    : RenglonesFiscales.NO_APLICA.equals(c.getRenglon()) ? "No va en el formato" : null);
        });
        Resumen r = AnalizadorConciliacion.analizar(cuentas, query.ajustes(id), cab.getTarifaRenta());
        r.setCabecera(cab);
        return r;
    }

    /** Borrador del formulario 110 sobre la conciliación (valores fiscales ya ajustados). */
    public Formulario110.Resultado formulario110(Integer empresaId, Long id, Formulario110.Manuales manuales) {
        Resumen r = obtener(empresaId, id);
        return Formulario110.calcular(r, query.cuentas(id), manuales != null ? manuales : new Formulario110.Manuales());
    }

    /** Las cuentas del balance cargado (para la pestaña Balance). */
    public List<Cuenta> cuentas(Integer empresaId, Long id) {
        exigir(empresaId, id);
        List<Cuenta> cuentas = query.cuentas(id);
        cuentas.forEach(c -> {
            var def = c.getRenglon() != null ? RenglonesFiscales.POR_CODIGO.get(c.getRenglon()) : null;
            c.setRenglonNombre(def != null ? def.nombre()
                    : RenglonesFiscales.NO_APLICA.equals(c.getRenglon()) ? "No va en el formato" : null);
        });
        return cuentas;
    }

    @Transactional
    public Cabecera actualizarTarifa(Integer empresaId, Long id, BigDecimal tarifa) {
        ConciliacionFiscalEntity c = editable(empresaId, id);
        validarTarifa(tarifa);
        c.setTarifaRenta(tarifa);
        conciliacionRepo.save(c);
        return query.cabecera(id, empresaId);
    }

    @Transactional
    public void eliminar(Integer empresaId, Long id) {
        conciliacionRepo.delete(editable(empresaId, id));
    }

    @Transactional
    public Cabecera cambiarEstado(Integer empresaId, Long id, boolean cerrar) {
        ConciliacionFiscalEntity c = exigir(empresaId, id);
        c.setEstado(cerrar ? ConciliacionFiscalEntity.ESTADO_CERRADA : ConciliacionFiscalEntity.ESTADO_BORRADOR);
        conciliacionRepo.save(c);
        return query.cabecera(id, empresaId);
    }

    // ── Balance ──────────────────────────────────────────────────────────────

    /** Lee el Excel; con {@code confirmar} reemplaza el balance cargado. */
    @Transactional
    public Resultado cargarExcel(Integer empresaId, Long id, List<FilaBalance> filas, boolean confirmar) {
        ConciliacionFiscalEntity c = editable(empresaId, id);
        Resultado r = new Resultado();
        List<Cuenta> cuentas = leerBalance(filas, r);
        if (confirmar && r.puedeConfirmarse()) {
            reemplazar(c, cuentas, "EXCEL");
            r.setConfirmado(true);
            r.getAvisos().add("Se cargaron " + cuentas.size() + " cuentas. Los ajustes que ya tenía se conservan.");
        }
        return r;
    }

    /** Trae el balance del año desde la contabilidad de Aura. */
    @Transactional
    public Resumen cargarSistema(Integer empresaId, Long id) {
        ConciliacionFiscalEntity c = editable(empresaId, id);
        List<Cuenta> cuentas = query.balanceSistema(empresaId, c.getAnio());
        if (cuentas.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La contabilidad no tiene movimientos para el año " + c.getAnio() + ".");
        }
        cuentas.forEach(x -> x.setRenglon(RenglonesFiscales.clasificar(x.getCodigo())));
        reemplazar(c, cuentas, "SISTEMA");
        return obtener(empresaId, id);
    }

    private void reemplazar(ConciliacionFiscalEntity c, List<Cuenta> cuentas, String origen) {
        query.borrarCuentas(c.getId());
        List<ConciliacionFiscalCuentaEntity> nuevas = new ArrayList<>();
        for (Cuenta x : cuentas) {
            nuevas.add(ConciliacionFiscalCuentaEntity.builder()
                    .conciliacionId(c.getId())
                    .codigo(x.getCodigo())
                    .nombre(x.getNombre() != null && x.getNombre().length() > 200 ? x.getNombre().substring(0, 200) : x.getNombre())
                    .saldo(x.getSaldo())
                    .renglon(x.getRenglon())
                    .renglonManual(false)
                    .build());
        }
        cuentaRepo.saveAll(nuevas);
        c.setOrigen(origen);
        c.setCargadoAt(LocalDateTime.now());
        conciliacionRepo.save(c);
    }

    /**
     * Filas del Excel → cuentas auxiliares con el saldo en el signo de su naturaleza.
     *
     * <ul>
     * <li>Con débito y crédito: saldo = débito − crédito, y se voltea en las clases 2, 3 y 4.</li>
     * <li>Con una sola columna de saldo: si la mayoría de las cuentas de pasivo,
     * patrimonio e ingreso vienen negativas, el archivo trae el débito positivo y
     * se voltean; si no, ya viene por naturaleza.</li>
     * <li>Si el archivo trae todos los niveles (1, 11, 1105, 110505) solo se toman
     * los auxiliares: si no, se sumaría dos veces.</li>
     * </ul>
     */
    static List<Cuenta> leerBalance(List<FilaBalance> filas, Resultado r) {
        if (filas == null || filas.isEmpty()) {
            r.getAvisos().add("El archivo no trae filas.");
            r.setErrores(1);
            return List.of();
        }
        Map<String, FilaBalance> porCodigo = new HashMap<>();
        Set<String> repetidos = new HashSet<>();
        List<FilaBalance> validas = new ArrayList<>();
        for (FilaBalance f : filas) {
            String codigo = f.getCuenta() != null ? f.getCuenta().replaceAll("[^0-9]", "") : "";
            if (codigo.isEmpty()) {
                error(r, f, "Falta el código de la cuenta (solo números).");
                continue;
            }
            if (f.getSaldo() == null && f.getDebito() == null && f.getCredito() == null) {
                error(r, f, "La cuenta " + codigo + " no trae saldo ni débito/crédito.");
                continue;
            }
            f.setCuenta(codigo);
            if (porCodigo.putIfAbsent(codigo, f) != null) {
                repetidos.add(codigo);
                error(r, f, "La cuenta " + codigo + " está repetida en el archivo.");
                continue;
            }
            validas.add(f);
        }

        // Solo auxiliares: fuera las cuentas que son prefijo de otra del archivo.
        Set<String> codigos = porCodigo.keySet();
        List<FilaBalance> hojas = new ArrayList<>();
        int padres = 0;
        for (FilaBalance f : validas) {
            String cod = f.getCuenta();
            boolean esPadre = codigos.stream().anyMatch(o -> o.length() > cod.length() && o.startsWith(cod));
            if (esPadre) padres++;
            else hojas.add(f);
        }
        if (padres > 0) {
            r.getAvisos().add("Se tomaron solo las cuentas auxiliares: " + padres
                    + " cuentas de nivel mayor se omitieron para no sumar dos veces.");
        }

        // ¿Qué convención de signo trae la columna de saldo?
        boolean debitoPositivo = false;
        boolean usaSaldo = hojas.stream().anyMatch(f -> f.getDebito() == null && f.getCredito() == null);
        if (usaSaldo) {
            int negativos = 0;
            int credito = 0;
            for (FilaBalance f : hojas) {
                if (f.getSaldo() == null || f.getSaldo().signum() == 0) continue;
                if (!RenglonesFiscales.naturalezaDebito(f.getCuenta())) {
                    credito++;
                    if (f.getSaldo().signum() < 0) negativos++;
                }
            }
            debitoPositivo = credito > 0 && negativos * 2 > credito;
            r.getAvisos().add(debitoPositivo
                    ? "La columna de saldo trae el débito positivo y el crédito negativo: se ajustó al signo de cada naturaleza."
                    : "La columna de saldo viene por naturaleza (pasivos, patrimonio e ingresos en positivo).");
        }

        List<Cuenta> cuentas = new ArrayList<>();
        int sinRenglon = 0;
        for (FilaBalance f : hojas) {
            String cod = f.getCuenta();
            boolean natDebito = RenglonesFiscales.naturalezaDebito(cod);
            BigDecimal saldo;
            if (f.getDebito() != null || f.getCredito() != null) {
                BigDecimal dc = nz(f.getDebito()).subtract(nz(f.getCredito()));
                saldo = natDebito ? dc : dc.negate();
            } else {
                saldo = debitoPositivo && !natDebito ? f.getSaldo().negate() : f.getSaldo();
            }
            Cuenta c = new Cuenta();
            c.setCodigo(cod);
            c.setNombre(f.getNombre());
            c.setSaldo(saldo.setScale(2, RoundingMode.HALF_UP));
            c.setRenglon(RenglonesFiscales.clasificar(cod));
            if (c.getRenglon() == null && saldo.signum() != 0) sinRenglon++;
            cuentas.add(c);

            ResultadoFila rf = new ResultadoFila();
            rf.setFila(f.getFila());
            rf.setEstado(c.getRenglon() == null ? "ADVERTENCIA" : "NUEVO");
            rf.setDetalle(cod + " " + (f.getNombre() != null ? f.getNombre() : ""));
            var def = c.getRenglon() != null ? RenglonesFiscales.POR_CODIGO.get(c.getRenglon()) : null;
            rf.setMensaje(def != null ? def.nombre()
                    : RenglonesFiscales.NO_APLICA.equals(c.getRenglon()) ? "No va en el formato" : "Sin renglón: clasifíquela después");
            r.getFilas().add(rf);
        }
        r.setNuevos(cuentas.size());
        if (sinRenglon > 0) {
            r.getAvisos().add(sinRenglon + " cuenta(s) no caen en ningún renglón: podrá clasificarlas a mano.");
        }
        return cuentas;
    }

    private static void error(Resultado r, FilaBalance f, String msg) {
        ResultadoFila rf = new ResultadoFila();
        rf.setFila(f.getFila());
        rf.setEstado("ERROR");
        rf.setDetalle(f.getCuenta());
        rf.setMensaje(msg);
        r.getFilas().add(rf);
        r.setErrores(r.getErrores() + 1);
    }

    @Transactional
    public Cuenta reclasificar(Integer empresaId, Long id, Long cuentaId, String renglon) {
        editable(empresaId, id);
        if (renglon != null && !renglon.isBlank() && !RenglonesFiscales.existe(renglon)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El renglón " + renglon + " no existe.");
        }
        ConciliacionFiscalCuentaEntity c = cuentaRepo.findById(cuentaId)
                .filter(x -> x.getConciliacionId().equals(id))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        c.setRenglon(renglon != null && !renglon.isBlank() ? renglon : null);
        c.setRenglonManual(true);
        cuentaRepo.save(c);
        return cuentas(empresaId, id).stream().filter(x -> x.getId().equals(cuentaId)).findFirst().orElse(null);
    }

    // ── Ajustes ──────────────────────────────────────────────────────────────

    @Transactional
    public Ajuste agregarAjuste(Integer empresaId, Long id, Ajuste dto) {
        editable(empresaId, id);
        if (dto.getRenglon() == null || !RenglonesFiscales.POR_CODIGO.containsKey(dto.getRenglon())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el renglón del ajuste.");
        }
        if (!"MENOR".equals(dto.getTipo()) && !"MAYOR".equals(dto.getTipo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique si es menor o mayor valor fiscal.");
        }
        if (!"PERMANENTE".equals(dto.getDiferencia()) && !"TEMPORARIA".equals(dto.getDiferencia())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique si la diferencia es permanente o temporaria.");
        }
        if (dto.getValor() == null || dto.getValor().signum() <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El valor del ajuste debe ser mayor que cero.");
        }
        if (dto.getConcepto() == null || dto.getConcepto().isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el concepto del ajuste.");
        }
        ConciliacionFiscalAjusteEntity a = ajusteRepo.save(ConciliacionFiscalAjusteEntity.builder()
                .conciliacionId(id)
                .renglon(dto.getRenglon())
                .tipo(dto.getTipo())
                .diferencia(dto.getDiferencia())
                .valor(dto.getValor().setScale(2, RoundingMode.HALF_UP))
                .concepto(dto.getConcepto().trim().length() > 300 ? dto.getConcepto().trim().substring(0, 300) : dto.getConcepto().trim())
                .cuentaCodigo(dto.getCuentaCodigo())
                .sugerencia(dto.getSugerencia())
                .build());
        dto.setId(a.getId());
        return dto;
    }

    @Transactional
    public void eliminarAjuste(Integer empresaId, Long id, Long ajusteId) {
        editable(empresaId, id);
        ConciliacionFiscalAjusteEntity a = ajusteRepo.findById(ajusteId)
                .filter(x -> x.getConciliacionId().equals(id))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Ajuste no encontrado"));
        ajusteRepo.delete(a);
    }

    // ── Guardas ──────────────────────────────────────────────────────────────

    private ConciliacionFiscalEntity exigir(Integer empresaId, Long id) {
        return conciliacionRepo.findById(id)
                .filter(c -> c.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Conciliación no encontrada"));
    }

    private ConciliacionFiscalEntity editable(Integer empresaId, Long id) {
        ConciliacionFiscalEntity c = exigir(empresaId, id);
        if (ConciliacionFiscalEntity.ESTADO_CERRADA.equals(c.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La conciliación está cerrada. Reábrala para cambiarla.");
        }
        return c;
    }

    private static void validarTarifa(BigDecimal t) {
        if (t == null || t.signum() < 0 || t.compareTo(new BigDecimal("100")) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La tarifa de renta debe estar entre 0 y 100.");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
