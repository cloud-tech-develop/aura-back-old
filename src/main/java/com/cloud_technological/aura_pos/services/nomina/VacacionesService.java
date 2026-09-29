package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.nomina.nomina.VacacionesSaldoDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.LiquidacionPrestacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import org.springframework.http.HttpStatus;

/**
 * Saldo de vacaciones (F3).
 *
 * <p>No hay una tabla de saldo que mantener sincronizada: se calcula al vuelo con
 * la antigüedad (15 días hábiles/año = 15/360 por día trabajado), el saldo inicial
 * traído de otro sistema y los días ya tomados (novedades VACACIONES).
 */
@Service
public class VacacionesService {

    /** 15 días hábiles por año trabajado. */
    private static final BigDecimal DIAS_VACACIONES_ANIO = new BigDecimal("15");
    private static final BigDecimal DIAS_ANIO = new BigDecimal("360");

    private final EmpleadoJPARepository empleadoRepo;
    private final NominaJPARepository nominaRepo;
    private final NominaConfigJPARepository configRepo;
    private final LiquidacionPrestacionJPARepository prestacionRepo;
    private final CalculadoraPrestaciones calculadora;

    public VacacionesService(EmpleadoJPARepository empleadoRepo,
                             NominaJPARepository nominaRepo,
                             NominaConfigJPARepository configRepo,
                             LiquidacionPrestacionJPARepository prestacionRepo,
                             CalculadoraPrestaciones calculadora) {
        this.empleadoRepo = empleadoRepo;
        this.nominaRepo = nominaRepo;
        this.configRepo = configRepo;
        this.prestacionRepo = prestacionRepo;
        this.calculadora = calculadora;
    }

    public VacacionesSaldoDto saldo(Long empleadoId, Integer empresaId) {
        EmpleadoEntity emp = empleadoRepo.findByIdAndEmpresaId(empleadoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        LocalDate ingreso = emp.getFechaIngreso();
        int antiguedad = ingreso != null
                ? Math.max(0, calculadora.diasComerciales(ingreso, LocalDate.now()))
                : 0;

        BigDecimal causados = BigDecimal.valueOf(antiguedad)
                .multiply(DIAS_VACACIONES_ANIO)
                .divide(DIAS_ANIO, 2, RoundingMode.HALF_UP);

        BigDecimal saldoInicial = emp.getVacacionesSaldoInicial() != null
                ? emp.getVacacionesSaldoInicial() : BigDecimal.ZERO;

        Long tomadosNomina = nominaRepo.sumDiasVacacionesTomados(empresaId, empleadoId);
        Long tomadosPrestacion = prestacionRepo.sumDiasVacacionesPrestacion(empresaId, empleadoId);
        int tomados = (tomadosNomina != null ? tomadosNomina.intValue() : 0)
                + (tomadosPrestacion != null ? tomadosPrestacion.intValue() : 0);

        BigDecimal disponibles = saldoInicial.add(causados)
                .subtract(BigDecimal.valueOf(tomados))
                .setScale(2, RoundingMode.HALF_UP);

        boolean anticipadas = configRepo.findByEmpresaId(empresaId)
                .map(c -> Boolean.TRUE.equals(c.getPermiteVacacionesAnticipadas()))
                .orElse(false);

        VacacionesSaldoDto dto = new VacacionesSaldoDto();
        dto.setEmpleadoId(empleadoId);
        dto.setFechaIngreso(ingreso);
        dto.setAntiguedadDias(antiguedad);
        dto.setDiasCausados(causados);
        dto.setSaldoInicial(saldoInicial);
        dto.setDiasTomados(tomados);
        dto.setDiasDisponibles(disponibles);
        dto.setPermiteAnticipadas(anticipadas);
        return dto;
    }
}
