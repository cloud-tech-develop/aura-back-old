package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.nomina.VacacionesSaldoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.FiltroVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RegistrarVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.ResultadoColectivasDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RetornoAnticipadoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionesColectivasDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Vacaciones (rediseño de nómina R7): se registran aquí, en tiempo o en dinero, y se
 * pagan en la nómina periódica del período como novedad. El saldo descuenta lo
 * registrado.
 */
public interface NominaVacacionService {

    /** Listado paginado. */
    PageImpl<VacacionDto> listar(PageableDto<FiltroVacacionDto> pageable, Integer empresaId);

    /** Unas vacaciones; 404 si no son de la empresa. */
    VacacionDto obtener(Long id, Integer empresaId);

    /** Saldo de días de la vinculación (de su empleado) a hoy. */
    VacacionesSaldoDto saldo(Long contratoId, Integer empresaId);

    /**
     * Registra vacaciones.
     *
     * <ul>
     *   <li>TIEMPO: del inicio al fin; los días son hábiles (sin domingos ni festivos)
     *       y el regreso es el siguiente día hábil. No se pueden cruzar con otras.</li>
     *   <li>DINERO: los días compensados no pasan de la mitad de lo causado (CST art. 189).</li>
     * </ul>
     *
     * 400 si faltan datos, no alcanza el saldo (salvo vacaciones anticipadas) o ya hay
     * una nómina aprobada o pagada en esas fechas; 409 si la vinculación no está activa.
     */
    VacacionDto registrar(RegistrarVacacionDto dto, Integer empresaId, Long usuarioId);

    /**
     * Vacaciones colectivas en tiempo: las mismas fechas para varias vinculaciones (o
     * todas las activas). A quien no se le puede registrar se omite con la causa.
     */
    ResultadoColectivasDto colectivas(VacacionesColectivasDto dto, Integer empresaId, Long usuarioId);

    /**
     * Regreso anticipado: los días hábiles desde la fecha de regreso hasta el fin
     * vuelven al saldo. 400 si no son en tiempo, la fecha no cae en el disfrute o esos
     * días ya están en una nómina aprobada o pagada.
     */
    VacacionDto retornoAnticipado(Long id, RetornoAnticipadoDto dto, Integer empresaId);

    /** Anula las vacaciones; 400 si ya están en una nómina aprobada o pagada. */
    VacacionDto anular(Long id, Integer empresaId);
}
