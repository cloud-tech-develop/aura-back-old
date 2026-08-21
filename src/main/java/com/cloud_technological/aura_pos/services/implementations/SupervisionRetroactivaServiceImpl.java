package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.caja.MovimientoRetroactivoDto;
import com.cloud_technological.aura_pos.dto.caja.SupervisionRetroactivaDto;
import com.cloud_technological.aura_pos.repositories.caja.SupervisionRetroactivaQueryRepository;
import com.cloud_technological.aura_pos.services.SupervisionRetroactivaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupervisionRetroactivaServiceImpl implements SupervisionRetroactivaService {

    static final String AUTORIZADO = "DOCUMENTO_AUTORIZADO";
    static final String OTRA_FECHA = "PAGO_DE_OTRA_FECHA";
    static final String CAJA_INFERIDA = "CAJA_INFERIDA";
    static final String AJUSTE = "AJUSTE_CIERRE";
    static final String SALIDA_OTRO_DIA = "SALIDA_CAJA_OTRO_DIA";

    private final SupervisionRetroactivaQueryRepository repository;

    @Override
    public SupervisionRetroactivaDto listar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();

        List<MovimientoRetroactivoDto> movimientos = repository.listar(empresaId, d, h);

        SupervisionRetroactivaDto resumen = new SupervisionRetroactivaDto();
        resumen.setMovimientos(movimientos);

        resumen.setCantidadAutorizados(contar(movimientos, AUTORIZADO));
        resumen.setMontoAutorizados(sumar(movimientos, AUTORIZADO));
        resumen.setCantidadOtrasFechas(contar(movimientos, OTRA_FECHA));
        resumen.setMontoOtrasFechas(sumar(movimientos, OTRA_FECHA));
        resumen.setCantidadCajaInferida(contar(movimientos, CAJA_INFERIDA));
        resumen.setMontoCajaInferida(sumar(movimientos, CAJA_INFERIDA));
        resumen.setCantidadAjustes(contar(movimientos, AJUSTE));
        resumen.setMontoAjustes(sumar(movimientos, AJUSTE));
        resumen.setCantidadSalidaOtroDia(contar(movimientos, SALIDA_OTRO_DIA));
        resumen.setMontoSalidaOtroDia(sumar(movimientos, SALIDA_OTRO_DIA));

        return resumen;
    }

    private static Integer contar(List<MovimientoRetroactivoDto> movimientos, String tipo) {
        return (int) movimientos.stream()
                .filter(m -> tipo.equals(m.getTipoHallazgo()))
                .count();
    }

    private static BigDecimal sumar(List<MovimientoRetroactivoDto> movimientos, String tipo) {
        return movimientos.stream()
                .filter(m -> tipo.equals(m.getTipoHallazgo()))
                .map(MovimientoRetroactivoDto::getMonto)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
