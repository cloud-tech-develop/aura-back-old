package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConfigDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Equipo;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.EquipoDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarOrden;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenEquipoEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioTecnicoConfigEntity;
import com.cloud_technological.aura_pos.services.servicio_tecnico.ReglasOrdenServicio;

/**
 * Servicio técnico: DTO ↔ entidad. Las listas del equipo (accesorios, estado físico)
 * se guardan como "A|B|C" y salen como lista.
 */
@Mapper(componentModel = "spring", imports = ReglasOrdenServicio.class)
public interface ServicioTecnicoMapper {

    @Mapping(target = "accesorios", expression = "java(ReglasOrdenServicio.separarLista(e.getAccesorios()))")
    @Mapping(target = "estadoFisico", expression = "java(ReglasOrdenServicio.separarLista(e.getEstadoFisico()))")
    EquipoDto toDto(ServicioOrdenEquipoEntity e);

    /** Copia los datos del formulario al equipo; IMEI y listas los pone el servicio ya validados. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresaId", ignore = true)
    @Mapping(target = "ordenId", ignore = true)
    @Mapping(target = "imei", ignore = true)
    @Mapping(target = "imei2", ignore = true)
    @Mapping(target = "accesorios", ignore = true)
    @Mapping(target = "estadoFisico", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void copiar(Equipo dto, @MappingTarget ServicioOrdenEquipoEntity e);

    /** Datos editables de la cabecera; estado, número, totales y fechas los maneja el servicio. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresaId", ignore = true)
    @Mapping(target = "sucursalId", ignore = true)
    @Mapping(target = "numero", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "prioridad", ignore = true)
    @Mapping(target = "garantiaDias", ignore = true)
    @Mapping(target = "fechaRecibido", ignore = true)
    @Mapping(target = "fechaEntregado", ignore = true)
    @Mapping(target = "totalRepuestos", ignore = true)
    @Mapping(target = "totalManoObra", ignore = true)
    @Mapping(target = "total", ignore = true)
    @Mapping(target = "totalAbonado", ignore = true)
    @Mapping(target = "ventaId", ignore = true)
    @Mapping(target = "motivoCancelacion", ignore = true)
    @Mapping(target = "facturaVentaId", ignore = true)
    @Mapping(target = "tokenPublico", ignore = true)
    @Mapping(target = "aprobadoClienteAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void copiar(GuardarOrden dto, @MappingTarget ServicioOrdenEntity o);

    @Mapping(target = "productoServicioNombre", ignore = true)
    ConfigDto toDto(ServicioTecnicoConfigEntity c);
}
