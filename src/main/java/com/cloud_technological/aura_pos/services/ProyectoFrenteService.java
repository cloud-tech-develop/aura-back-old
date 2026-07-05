package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.proyecto.AsignarTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.CreateFrenteDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateFrenteDto;

public interface ProyectoFrenteService {

    List<FrenteTableDto> listarPorProyecto(Long proyectoId, Integer empresaId);

    FrenteTableDto obtenerFrente(Long id, Integer empresaId);

    FrenteTableDto crearFrente(Long proyectoId, CreateFrenteDto dto, Integer empresaId, Long usuarioId);

    FrenteTableDto actualizarFrente(Long id, UpdateFrenteDto dto, Integer empresaId, Long usuarioId);

    void eliminarFrente(Long id, Integer empresaId, Long usuarioId);

    List<FrenteTrabajadorDto> listarTrabajadores(Long frenteId, Integer empresaId);

    void asignarTrabajador(Long frenteId, AsignarTrabajadorDto dto, Integer empresaId, Long usuarioId);

    void retirarTrabajador(Long frenteId, Long empleadoId, Integer empresaId, Long usuarioId);
}
