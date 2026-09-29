package com.cloud_technological.aura_pos.repositories.turno_caja;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;

public interface TurnoCajaJPARepository extends JpaRepository<TurnoCajaEntity, Long> {
    Optional<TurnoCajaEntity> findByIdAndCajaSucursalEmpresaId(Long id, Integer empresaId);
    // Verificar si ya hay un turno abierto en esa caja
    Optional<TurnoCajaEntity> findByCajaIdAndEstado(Long cajaId, String estado);
    // Turno abierto del usuario actual
    Optional<TurnoCajaEntity> findByUsuarioIdAndEstado(Long usuarioId, String estado);

    // Turno abierto de la sucursal, sin importar quién lo abrió. Lo usa el
    // resolutor de origen de fondos: el dinero pertenece a la caja, no al
    // usuario que digita el documento (un admin registrando un abono en
    // efectivo debe caer en el turno del cajero, no quedar en el aire).
    Optional<TurnoCajaEntity> findFirstByCajaSucursalIdAndEstadoOrderByFechaAperturaAsc(
            Integer sucursalId, String estado);

    // Todos los turnos abiertos de la sucursal. Inferir el turno solo es
    // legítimo cuando hay exactamente uno: con dos cajas abiertas, elegir "la
    // más antigua" es adivinar de cuál cajón salió la plata, y el descuadre le
    // cae a quien no gastó. Con más de uno el documento tiene que declararlo.
    List<TurnoCajaEntity> findByCajaSucursalIdAndEstado(Integer sucursalId, String estado);

    // Cajas abiertas de toda la empresa, para que el front pueda ofrecerlas al
    // elegir de qué caja sale o entra el dinero de un traslado.
    List<TurnoCajaEntity> findByCajaSucursalEmpresaIdAndEstadoOrderByFechaAperturaAsc(
            Integer empresaId, String estado);
}