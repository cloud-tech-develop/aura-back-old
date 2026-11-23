package com.cloud_technological.aura_pos.services.servicio_tecnico;

import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.AnularAbono;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.CambiarEstado;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteRapido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConfigDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConsultaPublica;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Entregar;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.FotoContenido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarConfig;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarItem;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarOrden;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Indicadores;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Nota;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenDetalle;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenFila;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Pago;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.RespuestaPresupuesto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Órdenes de servicio técnico (docs/PLAN_SERVICIO_TECNICO.md). La empresa y el
 * usuario siempre vienen del token; una orden de otra empresa responde 404.
 */
public interface ServicioOrdenService {

    /** Bandeja con filtros ({@code params}: estado, tecnicoId, soloMias, soloVencidas, desde, hasta). */
    PageImpl<OrdenFila> listar(PageableDto<Map<String, Object>> pageable, Integer empresaId, Long usuarioId);

    /** Conteos por estado, vencidas y entregadas del mes. */
    Indicadores indicadores(Integer empresaId);

    /** Orden completa con equipo, líneas, fotos, historial y estados permitidos. */
    OrdenDetalle obtener(Long id, Integer empresaId);

    /** Recibe un equipo: toma el consecutivo y deja la orden en RECIBIDO. */
    OrdenDetalle crear(GuardarOrden dto, Integer empresaId, Long sucursalId, Long usuarioId);

    /** Edita cliente, equipo, diagnóstico y fechas mientras la orden siga abierta. */
    OrdenDetalle actualizar(Long id, GuardarOrden dto, Integer empresaId, Long usuarioId);

    /** Pasa la orden a otro estado y lo deja en el historial. */
    OrdenDetalle cambiarEstado(Long id, CambiarEstado dto, Integer empresaId, Long usuarioId);

    /** Nota libre en el historial (también en órdenes cerradas). */
    OrdenDetalle agregarNota(Long id, Nota dto, Integer empresaId, Long usuarioId);

    /** Repuesto, mano de obra u otro; recalcula los totales. */
    OrdenDetalle agregarItem(Long id, GuardarItem dto, Integer empresaId);

    OrdenDetalle eliminarItem(Long id, Long itemId, Integer empresaId);

    /** Sube una foto (JPG, PNG o WEBP, máximo 3 MB) cifrada a la nube privada. */
    OrdenDetalle subirFoto(Long id, MultipartFile archivo, String momento, Integer empresaId, Long usuarioId);

    /** Contenido de una foto ya descifrado. */
    FotoContenido foto(Long id, Long fotoId, Integer empresaId);

    OrdenDetalle eliminarFoto(Long id, Long fotoId, Integer empresaId, Long usuarioId);

    /**
     * Abono del cliente: un recibo de caja sin facturas, que queda como anticipo y
     * se cruza con la factura al entregar.
     */
    OrdenDetalle registrarAbono(Long id, Pago dto, Integer empresaId, Integer sucursalId, Long usuarioId);

    /** Anula el abono (y su recibo de caja) si el anticipo no se ha cruzado. */
    OrdenDetalle anularAbono(Long id, Long abonoId, AnularAbono dto, Integer empresaId, Long usuarioId);

    /**
     * Entrega el equipo. Con factura: la arma con las líneas de la orden, la emite
     * cruzando los abonos y, si el cliente paga el saldo, registra el recibo. Todo o nada.
     */
    OrdenDetalle entregar(Long id, Entregar dto, Integer empresaId, Integer sucursalId, Long usuarioId);

    /** Lo que ve el cliente con el enlace (sin sesión); 404 si la clave no existe. */
    ConsultaPublica consultaPublica(String token);

    /** El cliente aprueba o rechaza el presupuesto desde el enlace. */
    ConsultaPublica responderPresupuesto(String token, RespuestaPresupuesto dto);

    /** Crea un cliente (tercero con rol cliente) con los datos mínimos de la recepción. */
    ClienteDto crearCliente(ClienteRapido dto, Integer empresaId);

    ConfigDto config(Integer empresaId);

    ConfigDto guardarConfig(GuardarConfig dto, Integer empresaId);
}
