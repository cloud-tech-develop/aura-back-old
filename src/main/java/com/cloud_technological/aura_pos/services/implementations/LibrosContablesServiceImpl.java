package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroAuxiliarDto;
import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroDiarioDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository.Partida;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository.SaldoAnterior;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.LibrosContablesService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LibrosContablesServiceImpl implements LibrosContablesService {

    /** El diario de un año cabe en pantalla e impresión; más que eso se pide por partes. */
    private static final long MAX_DIAS_DIARIO = 366;

    private final LibrosContablesQueryRepository queryRepo;
    private final EmpresaJPARepository empresaRepo;

    // ── Auxiliar por tercero ────────────────────────────────────────────────

    @Override
    public LibroAuxiliarDto auxiliarPorTercero(Integer empresaId, LocalDate desde, LocalDate hasta,
            String cuentaDesde, String cuentaHasta, Long terceroId) {
        validarRango(desde, hasta);

        // Cuenta (por código, en orden PUC) → tercero → acumulado.
        Map<String, LibroAuxiliarDto.Cuenta> cuentas = new TreeMap<>();
        Map<String, Map<String, LibroAuxiliarDto.Tercero>> terceros = new LinkedHashMap<>();

        for (SaldoAnterior s : queryRepo.saldosAnteriores(empresaId, desde,
                cuentaDesde, cuentaHasta, terceroId)) {
            LibroAuxiliarDto.Cuenta c = cuenta(cuentas, s.cuentaId(), s.cuentaCodigo(),
                    s.cuentaNombre(), s.naturaleza());
            LibroAuxiliarDto.Tercero t = tercero(terceros, c, s.terceroId(),
                    s.terceroDocumento(), s.terceroNombre());
            BigDecimal saldo = conSigno(c, s.saldo());
            t.setSaldoAnterior(t.getSaldoAnterior().add(saldo));
            c.setSaldoAnterior(c.getSaldoAnterior().add(saldo));
        }

        LibroAuxiliarDto dto = new LibroAuxiliarDto();
        empresaRepo.findById(empresaId).ifPresent(e -> {
            dto.setEmpresaNombre(e.getRazonSocial());
            dto.setNit(e.getNit());
        });
        dto.setDesde(desde);
        dto.setHasta(hasta);

        List<Partida> partidas = queryRepo.partidas(empresaId, desde, hasta,
                cuentaDesde, cuentaHasta, terceroId);
        for (Partida p : partidas) {
            LibroAuxiliarDto.Cuenta c = cuenta(cuentas, p.cuentaId(), p.cuentaCodigo(),
                    p.cuentaNombre(), p.naturaleza());
            LibroAuxiliarDto.Tercero t = tercero(terceros, c, p.terceroId(),
                    p.terceroDocumento(), p.terceroNombre());

            BigDecimal debito = nz(p.debito());
            BigDecimal credito = nz(p.credito());
            BigDecimal saldoPrevio = t.getLineas().isEmpty()
                    ? t.getSaldoAnterior()
                    : t.getLineas().get(t.getLineas().size() - 1).getSaldo();

            LibroAuxiliarDto.Linea l = new LibroAuxiliarDto.Linea();
            l.setFecha(p.fecha());
            l.setNumeroComprobante(p.numeroComprobante());
            l.setTipoOrigen(p.tipoOrigen());
            l.setDescripcion(p.descripcion());
            l.setDebito(debito);
            l.setCredito(credito);
            l.setSaldo(saldoPrevio.add(conSigno(c, debito.subtract(credito))));
            t.getLineas().add(l);

            t.setDebito(t.getDebito().add(debito));
            t.setCredito(t.getCredito().add(credito));
            c.setDebito(c.getDebito().add(debito));
            c.setCredito(c.getCredito().add(credito));
            dto.setTotalDebito(dto.getTotalDebito().add(debito));
            dto.setTotalCredito(dto.getTotalCredito().add(credito));
        }

        for (LibroAuxiliarDto.Cuenta c : cuentas.values()) {
            // Primero lo que no tiene tercero (caja, bancos, cierres) y luego
            // por nombre: así se busca a un proveedor sin leer toda la cuenta.
            List<LibroAuxiliarDto.Tercero> lista = terceros.get(c.getCodigo()).values().stream()
                    .sorted(Comparator.comparing((LibroAuxiliarDto.Tercero x) -> x.getTerceroId() != null)
                            .thenComparing(x -> x.getNombre() != null ? x.getNombre() : ""))
                    .toList();
            for (LibroAuxiliarDto.Tercero t : lista) {
                t.setSaldoFinal(t.getSaldoAnterior()
                        .add(conSigno(c, t.getDebito().subtract(t.getCredito()))));
            }
            c.getTerceros().addAll(lista);
            c.setSaldoFinal(c.getSaldoAnterior()
                    .add(conSigno(c, c.getDebito().subtract(c.getCredito()))));
            dto.getCuentas().add(c);
        }
        return dto;
    }

    private static LibroAuxiliarDto.Cuenta cuenta(Map<String, LibroAuxiliarDto.Cuenta> cuentas,
            Long id, String codigo, String nombre, String naturaleza) {
        return cuentas.computeIfAbsent(codigo, k -> {
            LibroAuxiliarDto.Cuenta c = new LibroAuxiliarDto.Cuenta();
            c.setCuentaId(id);
            c.setCodigo(codigo);
            c.setNombre(nombre);
            c.setNaturaleza(naturaleza);
            return c;
        });
    }

    private static LibroAuxiliarDto.Tercero tercero(
            Map<String, Map<String, LibroAuxiliarDto.Tercero>> terceros,
            LibroAuxiliarDto.Cuenta c, Long terceroId, String documento, String nombre) {
        return terceros.computeIfAbsent(c.getCodigo(), k -> new LinkedHashMap<>())
                .computeIfAbsent(Objects.toString(terceroId, "-"), k -> {
                    LibroAuxiliarDto.Tercero t = new LibroAuxiliarDto.Tercero();
                    t.setTerceroId(terceroId);
                    t.setDocumento(documento);
                    t.setNombre(terceroId == null ? "Sin tercero" : nombre);
                    return t;
                });
    }

    /** débito − crédito llevado a la naturaleza de la cuenta. */
    private static BigDecimal conSigno(LibroAuxiliarDto.Cuenta c, BigDecimal debitoMenosCredito) {
        BigDecimal v = nz(debitoMenosCredito);
        return "CREDITO".equalsIgnoreCase(c.getNaturaleza()) ? v.negate() : v;
    }

    // ── Libro diario ────────────────────────────────────────────────────────

    @Override
    public LibroDiarioDto diario(Integer empresaId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS_DIARIO) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El libro diario se consulta por máximo un año. Divida el rango");
        }

        LibroDiarioDto dto = new LibroDiarioDto();
        empresaRepo.findById(empresaId).ifPresent(e -> {
            dto.setEmpresaNombre(e.getRazonSocial());
            dto.setNit(e.getNit());
        });
        dto.setDesde(desde);
        dto.setHasta(hasta);

        Map<Long, LibroDiarioDto.Comprobante> porAsiento = new LinkedHashMap<>();
        for (Partida p : queryRepo.partidas(empresaId, desde, hasta, null, null, null)) {
            LibroDiarioDto.Comprobante c = porAsiento.computeIfAbsent(p.asientoId(), k -> {
                LibroDiarioDto.Comprobante n = new LibroDiarioDto.Comprobante();
                n.setAsientoId(p.asientoId());
                n.setFecha(p.fecha());
                n.setNumeroComprobante(p.numeroComprobante());
                n.setTipoOrigen(p.tipoOrigen());
                n.setDescripcion(p.descripcionAsiento());
                return n;
            });
            BigDecimal debito = nz(p.debito());
            BigDecimal credito = nz(p.credito());

            LibroDiarioDto.Linea l = new LibroDiarioDto.Linea();
            l.setCuentaCodigo(p.cuentaCodigo());
            l.setCuentaNombre(p.cuentaNombre());
            l.setTerceroDocumento(p.terceroDocumento());
            l.setTerceroNombre(p.terceroNombre());
            l.setDescripcion(p.descripcion());
            l.setDebito(debito);
            l.setCredito(credito);
            c.getLineas().add(l);

            c.setDebito(c.getDebito().add(debito));
            c.setCredito(c.getCredito().add(credito));
            dto.setTotalDebito(dto.getTotalDebito().add(debito));
            dto.setTotalCredito(dto.getTotalCredito().add(credito));
        }
        dto.getComprobantes().addAll(porAsiento.values());
        dto.setCuadrado(dto.getTotalDebito().subtract(dto.getTotalCredito()).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        return dto;
    }

    // ── Comunes ─────────────────────────────────────────────────────────────

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique las fechas desde y hasta");
        }
        if (desde.isAfter(hasta)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial es posterior a la final");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
