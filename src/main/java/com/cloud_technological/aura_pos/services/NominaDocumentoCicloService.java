package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.PagarDocumentoDto;

/**
 * Ciclo de la prenómina a la nómina (rediseño R4a): confirmar la prenómina, generar
 * la nómina (documento aparte, D1), confirmarla (asiento y nómina electrónica) y
 * anular.
 */
public interface NominaDocumentoCicloService {

    /**
     * Confirma una prenómina preliminar. 400/409 si es simulación, si quedan
     * empleados sin liquidar, si el total es cero o si el período contable está
     * cerrado.
     */
    DocumentoNominaDto confirmarPrenomina(Long id, Integer empresaId);

    /** Devuelve a preliminar una prenómina confirmada que aún no tiene nómina. */
    DocumentoNominaDto reabrirPrenomina(Long id, Integer empresaId);

    /**
     * Genera la nómina desde una prenómina confirmada: copia encabezado, empleados,
     * novedades y detalle a un documento NOM-xxxxx preliminar.
     *
     * @return la nómina creada
     */
    DocumentoNominaDto generarNomina(Long prenominaId, Integer empresaId, Long usuarioId);

    /**
     * Confirma la nómina: aprueba cada empleado (asiento contable y nómina
     * electrónica tras el commit). 409 si no está preliminar o el período contable
     * está cerrado.
     */
    DocumentoNominaDto confirmarNomina(Long id, Integer empresaId);

    /**
     * Anula un documento. Prenómina: solo preliminar. Nómina: preliminar o
     * confirmada; se anula cada empleado (reversa su asiento) y falla si alguno ya
     * está pagado.
     */
    DocumentoNominaDto anular(Long id, Integer empresaId);

    /**
     * Paga empleados de una nómina confirmada (los indicados o, si no viene ninguno,
     * todos los pendientes) con el mismo pago de la liquidación por períodos: medio
     * de pago obligatorio, saldo de la caja o cuenta y asiento de pago.
     */
    DocumentoNominaDto pagar(Long id, PagarDocumentoDto dto, Integer empresaId);
}
