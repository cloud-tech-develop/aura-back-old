package com.cloud_technological.aura_pos.services.documento_soporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Arma el cuerpo de {@code POST /v1/support-documents/validate} de Factus a
 * partir de datos ya extraídos de una compra o un gasto.
 *
 * <p>Es la v1, la misma versión con la que la cuenta ya factura y emite notas:
 * usa los IDs de Factus (tipo de documento, municipio, unidad) y no códigos
 * DIAN. El municipio de Aura ya es el ID de Factus (la tabla se sembró de ahí).
 *
 * <p>Es puro a propósito: no lee la base ni llama a Factus, así que las reglas
 * se prueban sin red. Lo que falta para emitir se devuelve como lista de
 * faltantes en vez de lanzar: el usuario ve todos los problemas de una vez.
 */
public final class DocumentoSoporteArmador {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal CIEN = new BigDecimal("100");

    /** ID de Factus del NIT. */
    private static final int NIT = 6;

    /**
     * Tipo de documento del tercero → ID de Factus v1, solo los que el
     * documento soporte admite. La cédula NO está: una persona natural
     * residente se identifica con su NIT, que es su cédula más el dígito de
     * verificación.
     */
    private static final Map<String, Integer> TIPOS_DOCUMENTO = Map.of(
            "NIT", NIT,
            "CC", NIT,
            "TE", 4,
            "CE", 5,
            "PASAPORTE", 7,
            "PA", 7,
            "DIE", 8,
            "PEP", 9,
            "NIT_EXTRANJERO", 10);

    /** Medio de pago de Aura → código DIAN (la v1 lo recibe como código). */
    private static final Map<String, String> MEDIOS_PAGO = Map.of(
            "EFECTIVO", "10",
            "TRANSFERENCIA", "47",
            "CONSIGNACION", "42",
            "TARJETA", "48",
            "TARJETA_CREDITO", "48",
            "TARJETA_DEBITO", "49",
            "NEQUI", "47",
            "DAVIPLATA", "47",
            "CHEQUE", "20");

    /** Medio de pago no definido: lo que se declara cuando la compra es a crédito. */
    private static final String MEDIO_NO_DEFINIDO = "1";

    /** Pesos del dígito de verificación DIAN, del dígito menos significativo al más. */
    private static final int[] PESOS_DV = { 3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71 };

    public record Proveedor(String tipoDocumento, String numeroDocumento, String dv,
            String nombre, String nombreComercial, String direccion, String codigoPais,
            Long municipioId, String email, String telefono, String regimen) {
    }

    /** Precio unitario SIN IVA; descuento e IVA en porcentaje. */
    public record Item(String codigo, String nombre, BigDecimal cantidad, BigDecimal precio,
            BigDecimal descuentoPct, BigDecimal ivaPct) {
    }

    /**
     * Porcentajes de retención tal como los aplicó el documento de origen. El
     * documento soporte solo informa ReteRenta y ReteIVA; la ReteICA no va.
     */
    public record Retenciones(BigDecimal retefuentePct, BigDecimal reteivaPct,
            BigDecimal valorTotal) {
    }

    public record Pago(boolean credito, String metodoPago) {
    }

    public record Resultado(ObjectNode payload, BigDecimal total, BigDecimal iva,
            BigDecimal retenciones, BigDecimal netoAPagar,
            List<String> faltantes, List<String> advertencias) {

        public boolean puedeEmitirse() {
            return faltantes.isEmpty();
        }
    }

    private DocumentoSoporteArmador() {
    }

    public static Resultado armar(String referenceCode, String numberingRangeId, String observacion,
            Proveedor p, List<Item> items, Retenciones ret, Pago pago) {
        List<String> faltantes = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        ObjectNode body = MAPPER.createObjectNode();
        body.put("reference_code", referenceCode);
        if (numberingRangeId != null && !numberingRangeId.isBlank()) {
            try {
                body.put("numbering_range_id", Integer.parseInt(numberingRangeId.trim()));
            } catch (NumberFormatException e) {
                faltantes.add("El rango de numeración debe ser un número (ID del rango en Factus)");
            }
        }
        boolean credito = pago != null && pago.credito();
        body.put("payment_method_code", credito ? MEDIO_NO_DEFINIDO
                : MEDIOS_PAGO.getOrDefault(normalizar(pago != null ? pago.metodoPago() : null), "10"));
        String obs = observacion != null ? observacion.trim() : "";
        body.put("observation", obs.length() > 250 ? obs.substring(0, 250) : obs);

        body.set("provider", proveedor(p, faltantes, advertencias));

        // ── Ítems ──
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal ivaIncluido = BigDecimal.ZERO;
        ArrayNode arr = body.putArray("items");
        if (items == null || items.isEmpty()) {
            faltantes.add("El documento no tiene ítems");
        } else {
            for (Item it : items) {
                BigDecimal cantidad = nz(it.cantidad()).abs();
                BigDecimal precio = nz(it.precio()).abs();
                BigDecimal desc = nz(it.descuentoPct());
                BigDecimal ivaPct = nz(it.ivaPct());
                String nombre = texto(it.nombre(), "Ítem");
                if (cantidad.signum() == 0 || precio.signum() == 0) {
                    faltantes.add("El ítem \"" + nombre + "\" no tiene cantidad o precio");
                }

                // El documento soporte no lleva impuestos: quien no está
                // obligado a facturar no cobra IVA. Si la compra lo registró,
                // se reporta lo que efectivamente se pagó y se avisa.
                if (ivaPct.signum() > 0) {
                    BigDecimal conIva = precio.multiply(CIEN.add(ivaPct)).divide(CIEN, 2, RoundingMode.HALF_UP);
                    ivaIncluido = ivaIncluido.add(conIva.subtract(precio).multiply(cantidad));
                    precio = conIva;
                }

                // Factus pide cantidad entera. Una compra por kilos o litros se
                // reporta como una línea por el valor total, con la cantidad
                // real en el nombre para que el soporte se entienda.
                BigDecimal cantidadEnviada = cantidad;
                if (cantidad.stripTrailingZeros().scale() > 0) {
                    precio = precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
                    nombre = nombre + " (" + cantidad.stripTrailingZeros().toPlainString() + ")";
                    cantidadEnviada = BigDecimal.ONE;
                }

                BigDecimal bruto = cantidadEnviada.multiply(precio);
                total = total.add(bruto.subtract(bruto.multiply(desc).divide(CIEN, 2, RoundingMode.HALF_UP)));

                ObjectNode n = arr.addObject();
                n.put("code_reference", texto(it.codigo(), "SIN-REF"));
                n.put("name", nombre);
                n.put("quantity", cantidadEnviada.intValueExact());
                n.put("discount_rate", desc.setScale(2, RoundingMode.HALF_UP));
                n.put("price", precio.setScale(2, RoundingMode.HALF_UP));
                n.put("unit_measure_id", 70);
                n.put("standard_code_id", 1);
                retencionesItem(n, ret);
            }
        }
        if (ivaIncluido.signum() > 0) {
            advertencias.add("La compra registra IVA, pero quien no está obligado a facturar no "
                    + "cobra IVA: el documento soporte va por el valor pagado y ese IVA no es "
                    + "descontable. Revise la compra");
        }

        total = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal retenciones = ret != null ? nz(ret.valorTotal()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new Resultado(body, total, ivaIncluido.setScale(2, RoundingMode.HALF_UP),
                retenciones, total.subtract(retenciones), faltantes, advertencias);
    }

    private static ObjectNode proveedor(Proveedor p, List<String> faltantes, List<String> advertencias) {
        ObjectNode n = MAPPER.createObjectNode();
        if (p == null) {
            faltantes.add("El documento no tiene proveedor");
            return n;
        }
        String tipo = normalizar(p.tipoDocumento());
        Integer tipoId = TIPOS_DOCUMENTO.get(tipo);
        if (tipoId == null) {
            faltantes.add("El documento soporte no admite el tipo de documento \"" + p.tipoDocumento()
                    + "\" del proveedor (admite NIT, cédula, cédula o tarjeta de extranjería, "
                    + "pasaporte y PEP)");
        }
        String numero = texto(p.numeroDocumento(), "").replaceAll("[^0-9A-Za-z]", "");
        if (numero.isEmpty()) faltantes.add("El proveedor no tiene número de documento");
        if (vacio(p.nombre())) faltantes.add("El proveedor no tiene nombre");
        if (vacio(p.direccion())) faltantes.add("El proveedor no tiene dirección");
        if (vacio(p.email())) faltantes.add("El proveedor no tiene correo electrónico");
        String pais = vacio(p.codigoPais()) ? "CO" : p.codigoPais().trim().toUpperCase();
        if ("CO".equals(pais) && p.municipioId() == null) {
            faltantes.add("El proveedor no tiene municipio");
        }
        // El documento soporte es para quien NO está obligado a facturar. Si
        // el tercero es responsable de IVA, lo correcto es pedirle la factura.
        if ("RESPONSABLE_IVA".equalsIgnoreCase(p.regimen())) {
            advertencias.add("El proveedor está marcado como responsable de IVA: normalmente "
                    + "está obligado a facturar y lo que corresponde es pedirle factura electrónica");
        }

        n.put("identification_document_id", tipoId != null ? tipoId : 0);
        n.put("identification", numero);
        if (tipoId != null && tipoId == NIT && numero.matches("\\d+")) {
            // La cédula viaja como NIT: su DV se calcula, no se inventa.
            int dv = !vacio(p.dv()) && "NIT".equals(tipo) && p.dv().trim().matches("\\d")
                    ? Integer.parseInt(p.dv().trim())
                    : digitoVerificacion(numero);
            n.put("dv", dv);
        }
        n.put("trade_name", texto(p.nombreComercial(), ""));
        n.put("names", texto(p.nombre(), "").trim());
        n.put("address", texto(p.direccion(), "").trim());
        n.put("email", texto(p.email(), "").trim());
        if (!vacio(p.telefono())) n.put("phone", p.telefono().trim());
        n.put("is_resident", "CO".equals(pais) ? 1 : 0);
        n.put("country_code", pais);
        if (p.municipioId() != null) n.put("municipality_id", p.municipioId());
        return n;
    }

    /** 06 ReteRenta y 05 ReteIVA: las únicas que admite el documento soporte. */
    private static void retencionesItem(ObjectNode item, Retenciones ret) {
        if (ret == null) return;
        ArrayNode arr = null;
        Object[][] tipos = { { "06", ret.retefuentePct() }, { "05", ret.reteivaPct() } };
        for (Object[] t : tipos) {
            BigDecimal pct = nz((BigDecimal) t[1]);
            if (pct.signum() > 0) {
                if (arr == null) arr = item.putArray("withholding_taxes");
                ObjectNode w = arr.addObject();
                w.put("code", (String) t[0]);
                w.put("withholding_tax_rate", pct.setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
        }
    }

    /** Dígito de verificación del NIT según el algoritmo de la DIAN (módulo 11). */
    public static int digitoVerificacion(String nit) {
        String n = nit.replaceAll("\\D", "");
        int suma = 0;
        for (int i = 0; i < n.length() && i < PESOS_DV.length; i++) {
            int digito = n.charAt(n.length() - 1 - i) - '0';
            suma += digito * PESOS_DV[i];
        }
        int residuo = suma % 11;
        return residuo > 1 ? 11 - residuo : residuo;
    }

    private static String normalizar(String s) {
        return s == null ? "" : s.trim().toUpperCase().replace(' ', '_').replace(".", "");
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String texto(String s, String def) {
        return vacio(s) ? def : s;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
