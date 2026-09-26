package com.cloud_technological.aura_pos.dto.cartera.agenda;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** "Cobrar hoy": lo que el cobrador tiene que atender, en orden de urgencia. */
@Getter
@Setter
public class AgendaCobroDto {
    private AgendaResumenDto resumen;
    private List<PromesaAgendaDto> promesasHoy;
    private List<PromesaAgendaDto> promesasIncumplidas;
    private List<PromesaAgendaDto> promesasProximas;
    private List<FacturaAgendaDto> porVencer;
    private List<ClienteAgendaDto> vencidosSinGestion;
}
