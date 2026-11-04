package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoAdjuntoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoTopeHorasDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CopiaPlantillaDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoNovedadDto;

/**
 * Plantilla de nómina del cargo (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md, F5): los
 * conceptos que liquida siempre, las novedades que se le pueden reportar, el
 * máximo de horas extra y recargos, y sus adjuntos. Todas las operaciones
 * responden 404 si el cargo no es de la empresa.
 */
public interface NominaCargoPlantillaService {

    /** Conceptos fijos del cargo. */
    List<CargoConceptoDto> conceptos(Long cargoId, Integer empresaId);

    /**
     * Agrega conceptos fijos por número. Los que ya estaban se ignoran; 400 si un
     * número no existe o ya está en las novedades permitidas del cargo.
     *
     * @return la lista completa de conceptos del cargo
     */
    List<CargoConceptoDto> agregarConceptos(Long cargoId, List<Integer> numeros, Integer empresaId);

    /** Cambia cantidad, valor, porcentaje u observación de un concepto del cargo. */
    List<CargoConceptoDto> actualizarConcepto(Long cargoId, Long filaId, GuardarCargoConceptoDto dto, Integer empresaId);

    /** Quita un concepto del cargo. */
    List<CargoConceptoDto> quitarConcepto(Long cargoId, Long filaId, Integer empresaId);

    /** Novedades permitidas del cargo. */
    List<CargoNovedadDto> novedades(Long cargoId, Integer empresaId);

    /** Agrega novedades permitidas por número, con las mismas reglas que los conceptos. */
    List<CargoNovedadDto> agregarNovedades(Long cargoId, List<Integer> numeros, Integer empresaId);

    /** Cambia topes y autorización de una novedad del cargo. */
    List<CargoNovedadDto> actualizarNovedad(Long cargoId, Long filaId, GuardarCargoNovedadDto dto, Integer empresaId);

    /** Quita una novedad permitida del cargo. */
    List<CargoNovedadDto> quitarNovedad(Long cargoId, Long filaId, Integer empresaId);

    /** Los siete tipos de hora extra y recargo con el tope del cargo. */
    List<CargoTopeHorasDto> topes(Long cargoId, Integer empresaId);

    /**
     * Reemplaza los topes de horas del cargo. Valida los límites legales de horas
     * extra (2 al día, 12 a la semana) y que un cargo de nivel directivo no tenga
     * horas extra (art. 162 CST).
     */
    List<CargoTopeHorasDto> guardarTopes(Long cargoId, List<CargoTopeHorasDto> topes, Integer empresaId);

    /** Adjuntos del cargo. */
    List<CargoAdjuntoDto> adjuntos(Long cargoId, Integer empresaId);

    /** Sube un adjunto a R2 (PDF, imagen, Excel o Word; máximo 10 MB). */
    CargoAdjuntoDto subirAdjunto(Long cargoId, MultipartFile archivo, Integer empresaId, Integer usuarioId);

    /** Quita un adjunto (borrado lógico). */
    void eliminarAdjunto(Long cargoId, Long adjuntoId, Integer empresaId);

    /**
     * Copia a este cargo los conceptos, novedades y topes de otro. Lo que ya
     * estaba no se toca.
     */
    CopiaPlantillaDto copiarDe(Long cargoId, Long origenId, Integer empresaId);
}
