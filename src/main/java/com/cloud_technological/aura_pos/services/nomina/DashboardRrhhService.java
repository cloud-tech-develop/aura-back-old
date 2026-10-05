package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto;
import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto.EstadoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto.NominaMesDto;
import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto.PersonalDto;
import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto.RubroCostoDto;
import com.cloud_technological.aura_pos.repositories.nomina.DashboardRrhhQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.DashboardRrhhQueryRepository.PeriodoMes;
import com.cloud_technological.aura_pos.repositories.nomina.DashboardRrhhQueryRepository.TotalesMes;

/** Resumen del Centro de Recursos Humanos: KPIs del mes, serie del año, costo por rubro y pendientes. */
@Service
public class DashboardRrhhService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    @Autowired
    private DashboardRrhhQueryRepository repo;

    @Transactional(readOnly = true)
    public DashboardRrhhDto resumen(Integer empresaId, int anio, int mes) {
        YearMonth actual = YearMonth.of(anio, mes);
        YearMonth anterior = actual.minusMonths(1);

        // Una sola consulta cubre la serie (enero..mes) y el mes anterior,
        // que en enero cae en el año pasado.
        YearMonth desdeMes = anterior.isBefore(YearMonth.of(anio, 1)) ? anterior : YearMonth.of(anio, 1);
        Map<YearMonth, NominaMesDto> porMes = new LinkedHashMap<>();
        for (YearMonth m = desdeMes; !m.isAfter(actual); m = m.plusMonths(1)) {
            porMes.put(m, vacio(m));
        }
        TotalesMes delMes = null;
        for (TotalesMes t : repo.totalesPorMes(empresaId, desdeMes.atDay(1), actual.atEndOfMonth())) {
            YearMonth ym = YearMonth.of(t.anio(), t.mes());
            NominaMesDto r = porMes.get(ym);
            if (r == null) continue;
            r.setDevengado(t.devengado());
            r.setAportes(t.seguridadSocial().add(t.parafiscales()));
            r.setProvisiones(t.provisiones());
            r.setNeto(t.neto());
            r.setEmpleadosLiquidados(t.empleados());
            if (ym.equals(actual)) delMes = t;
        }

        DashboardRrhhDto dto = new DashboardRrhhDto();
        dto.setAnio(anio);
        dto.setMes(mes);
        dto.setMesActual(porMes.get(actual));
        dto.setMesAnterior(porMes.get(anterior));
        porMes.forEach((m, r) -> {
            if (m.getYear() == anio) dto.getSerie().add(r);
        });
        if (delMes != null) dto.setDistribucionCosto(rubros(delMes));
        dto.setPersonal(personal(empresaId, actual, anterior));
        dto.setEstado(estado(empresaId, actual));
        return dto;
    }

    private PersonalDto personal(Integer empresaId, YearMonth actual, YearMonth anterior) {
        // Para el mes en curso se cuenta a hoy: el cierre del mes aún no ha llegado.
        LocalDate hoy = LocalDate.now();
        LocalDate corte = actual.equals(YearMonth.from(hoy)) ? hoy : actual.atEndOfMonth();
        PersonalDto p = new PersonalDto();
        p.setActivos(repo.activosAl(empresaId, corte));
        p.setActivosMesAnterior(repo.activosAl(empresaId, anterior.atEndOfMonth()));
        p.setIngresosMes(repo.ingresos(empresaId, actual.atDay(1), actual.atEndOfMonth()));
        p.setRetirosMes(repo.retiros(empresaId, actual.atDay(1), actual.atEndOfMonth()));
        return p;
    }

    private EstadoNominaDto estado(Integer empresaId, YearMonth mes) {
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        EstadoNominaDto e = new EstadoNominaDto();
        PeriodoMes periodo = repo.ultimoPeriodo(empresaId, desde, hasta);
        e.setPeriodoEstado(periodo != null ? periodo.estado() : "SIN_PERIODO");
        if (periodo != null) {
            e.setPeriodoDescripcion(periodo.inicio().format(DIA) + " – " + periodo.fin().format(DIA));
        }
        e.setNominasBorrador(repo.nominasEnEstado(empresaId, desde, hasta, "BORRADOR"));
        e.setNominasPorPagar(repo.nominasEnEstado(empresaId, desde, hasta, "APROBADO"));
        e.setNovedadesPendientes(repo.novedadesPendientes(empresaId));
        LocalDate hoy = LocalDate.now();
        e.setContratosPorVencer(repo.contratosPorVencer(empresaId, hoy, hoy.plusDays(30)));
        return e;
    }

    private static List<RubroCostoDto> rubros(TotalesMes t) {
        return java.util.stream.Stream.of(
                rubro("SALARIOS", "Salarios", t.salarios()),
                rubro("AUXILIO", "Auxilio de transporte", t.auxilio()),
                rubro("OTROS", "Horas extra y otros devengos", t.otrosDevengos()),
                rubro("SEGURIDAD", "Seguridad social", t.seguridadSocial()),
                rubro("PARAFISCALES", "Parafiscales", t.parafiscales()),
                rubro("PRESTACIONES", "Prestaciones sociales", t.provisiones()))
                .filter(r -> r.getValor().signum() != 0)
                .sorted((a, b) -> b.getValor().compareTo(a.getValor()))
                .toList();
    }

    private static RubroCostoDto rubro(String codigo, String nombre, BigDecimal valor) {
        RubroCostoDto r = new RubroCostoDto();
        r.setCodigo(codigo);
        r.setNombre(nombre);
        r.setValor(valor != null ? valor : BigDecimal.ZERO);
        return r;
    }

    private static NominaMesDto vacio(YearMonth m) {
        NominaMesDto r = new NominaMesDto();
        r.setAnio(m.getYear());
        r.setMes(m.getMonthValue());
        return r;
    }
}
