package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import com.cloud_technological.aura_pos.dto.caja.SupervisionRetroactivaDto;

/**
 * Qué entró a las cajas sin ser del turno.
 *
 * <p>El resto del módulo evita que los documentos viejos descuadren un arqueo,
 * o deja el rastro cuando sí pasan. Esto es lo que faltaba del otro lado: un
 * sitio donde el administrador pueda mirar ese rastro sin abrir turno por turno.
 *
 * <p>No es una bandeja de pendientes. Nada de lo que aparece aquí está a medio
 * clasificar — el origen es obligatorio al registrar y el freno bloquea lo que
 * no debe pasar. Es supervisión: qué se autorizó, quién lo autorizó y por qué.
 */
public interface SupervisionRetroactivaService {

    /** @param desde y hasta opcionales; sin ellos, el mes en curso. */
    SupervisionRetroactivaDto listar(Integer empresaId, LocalDate desde, LocalDate hasta);
}
