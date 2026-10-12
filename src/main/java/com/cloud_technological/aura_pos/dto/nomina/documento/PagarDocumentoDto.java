package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.util.ArrayList;
import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;

import lombok.Getter;
import lombok.Setter;

/** Pagar empleados de una nómina confirmada: cuáles (vacío = todos los pendientes) y cómo. */
@Getter
@Setter
public class PagarDocumentoDto {
    private List<Long> nominaIds = new ArrayList<>();
    private PagoNominaDto pago;
}
