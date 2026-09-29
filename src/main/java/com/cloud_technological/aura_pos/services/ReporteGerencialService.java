package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaResultadoDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDetalleDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDto;
import com.cloud_technological.aura_pos.dto.auditoria.Severidad;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDetalladoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraResumenDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosLineaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosResumenDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteResumenAvanzadoDto;
import com.cloud_technological.aura_pos.repositories.cartera.ReporteCarteraQueryRepository;
import com.cloud_technological.aura_pos.repositories.gastos.ReporteGastosQueryRepository;
import com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository;
import com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository.MovimientoPorFamilia;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraTerceroDto;
import com.cloud_technological.aura_pos.services.implementations.CarritoAbandonadoService;
import com.cloud_technological.aura_pos.utils.GraficaPdf;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import lombok.extern.slf4j.Slf4j;

/**
 * Reporte gerencial de auditoría en PDF (fases G2–G4).
 *
 * <p>Compone lo que ya calculan los servicios existentes y le pega encima los
 * hallazgos de {@link AuditoriaService}. <b>No recalcula reglas de negocio</b>:
 * si una cifra sale mal, se arregla donde se produce — un reporte que corrige al
 * vuelo esconde el problema que existe para destapar.
 *
 * <p>El semáforo va en la portada, no al final. Quien lo abre quiere saber si
 * tiene un problema, no leer doce páginas hasta encontrarlo.
 *
 * <p>Las secciones 1–9 hablan en lenguaje del dueño del negocio; los anexos son
 * densos a propósito, porque es donde el contador va a buscar el soporte.
 */
@Slf4j
@Service
public class ReporteGerencialService {

    private static final DeviceRgb AZUL   = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb GRIS   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb ZEBRA  = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb BLANCO = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb ROJO   = new DeviceRgb(185, 28, 28);
    private static final DeviceRgb AMBAR  = new DeviceRgb(180, 83, 9);
    private static final DeviceRgb VERDE  = new DeviceRgb(21, 128, 61);

    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuditoriaService auditoriaService;
    private final AsientoContableService contabilidad;
    private final ReporteAvanzadoService avanzado;
    private final ReporteCarteraQueryRepository carteraRepo;
    private final ReporteGastosQueryRepository gastosRepo;
    private final KardexQueryRepository kardexRepo;
    private final IEmpresaService empresaService;
    private final SecurityUtils securityUtils;
    private final CarritoAbandonadoService carritos;
    private final DeclaracionesService declaraciones;

    public ReporteGerencialService(AuditoriaService auditoriaService,
            AsientoContableService contabilidad, ReporteAvanzadoService avanzado,
            ReporteCarteraQueryRepository carteraRepo, ReporteGastosQueryRepository gastosRepo,
            KardexQueryRepository kardexRepo,
            IEmpresaService empresaService, SecurityUtils securityUtils,
            CarritoAbandonadoService carritos, DeclaracionesService declaraciones) {
        this.auditoriaService = auditoriaService;
        this.contabilidad = contabilidad;
        this.avanzado = avanzado;
        this.carteraRepo = carteraRepo;
        this.gastosRepo = gastosRepo;
        this.kardexRepo = kardexRepo;
        this.empresaService = empresaService;
        this.securityUtils = securityUtils;
        this.carritos = carritos;
        this.declaraciones = declaraciones;
    }

    @Transactional(readOnly = true)
    public byte[] generarPdf(AuditoriaFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();

        // El detalle se enciende siempre: el anexo B es la mitad del valor del
        // documento. Sin él, un hallazgo es una cifra que nadie puede ir a mirar.
        filtro.setIncluirDetalle(Boolean.TRUE);
        AuditoriaResultadoDto auditoria = auditoriaService.auditar(filtro);

        LocalDate desde = auditoria.getFechaDesde();
        LocalDate hasta = auditoria.getFechaHasta();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(28, 32, 28, 32);

            portada(doc, empresaId, desde, hasta, auditoria);
            ReporteResumenAvanzadoDto ventas = comoVaElNegocio(doc, empresaId, desde, hasta);
            ventasPerdidas(doc, empresaId, desde, hasta, ventas);
            cartera(doc, empresaId, desde, hasta);
            gastos(doc, empresaId, desde, hasta);
            inventario(doc, pdf, empresaId, desde, hasta);
            contabilidadSeccion(doc, empresaId, desde, hasta);
            impuestos(doc, empresaId, desde, hasta);
            hallazgos(doc, auditoria);
            anexoMetodologia(doc, auditoria);
            anexoDetalle(doc, auditoria);

            doc.close();
            log.info("[Reporte gerencial] Empresa {} — {} a {}: {} hallazgos ALTA",
                    empresaId, desde, hasta, auditoria.getAlta());
            return out.toByteArray();

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Reporte gerencial] Error generando el PDF", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el reporte gerencial: " + ex.getMessage());
        }
    }

    // ── Portada y semáforo ────────────────────────────────────────────

    private void portada(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta,
            AuditoriaResultadoDto a) {
        String nombre = "";
        String nit = "";
        try {
            EmpresaDto e = empresaService.obtenerEmpresaActual(empresaId, null, null);
            nombre = e.getRazonSocial() != null ? e.getRazonSocial() : "";
            nit = e.getNit() != null ? "NIT " + e.getNit() : "";
        } catch (RuntimeException ex) {
            log.warn("[Reporte gerencial] Sin datos de empresa: {}", ex.getMessage());
        }

        Table banner = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
        banner.addCell(new Cell()
                .add(new Paragraph("REPORTE GERENCIAL DE AUDITORÍA")
                        .setBold().setFontSize(18).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(nombre + (nit.isEmpty() ? "" : "  ·  " + nit))
                        .setFontSize(10).setFontColor(ColorConstants.WHITE).setMarginTop(2))
                .add(new Paragraph("Del " + desde.format(F_FECHA) + " al " + hasta.format(F_FECHA)
                        + "  ·  Generado el " + LocalDate.now().format(F_FECHA))
                        .setFontSize(9).setFontColor(ColorConstants.LIGHT_GRAY))
                .setBackgroundColor(AZUL).setBorder(Border.NO_BORDER).setPadding(16));
        doc.add(banner);
        espacio(doc, 10);

        // El semáforo abre el documento: es lo primero que alguien quiere saber.
        doc.add(new Paragraph(a.estaLimpio()
                ? "El período cierra sin hallazgos graves."
                : "Hay " + a.getAlta() + " asunto(s) que requieren revisión.")
                .setBold().setFontSize(13)
                .setFontColor(a.estaLimpio() ? VERDE : ROJO));

        Table sem = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
                .useAllAvailableWidth().setMarginTop(6);
        tarjeta(sem, "Graves", String.valueOf(a.getAlta()),
                "Plata sin explicar o contabilidad que no cierra", ROJO);
        tarjeta(sem, "Por revisar", String.valueOf(a.getMedia()),
                "Cuadra pero no se puede defender", AMBAR);
        tarjeta(sem, "Menores", String.valueOf(a.getBaja()),
                "Higiene de datos", GRIS);
        tarjeta(sem, "En riesgo", pesos(a.getMontoEnRiesgo()),
                "Suma de los hallazgos graves", AZUL);
        doc.add(sem);

        espacio(doc, 8);
        doc.add(new Paragraph("Cada cifra de este reporte se obtuvo cruzando dos fuentes "
                + "independientes del sistema. El anexo A dice cuáles.")
                .setFontSize(8).setItalic().setFontColor(GRIS));
    }

    /** Una tarjeta del semáforo. */
    private void tarjeta(Table t, String etiqueta, String valor, String nota, DeviceRgb color) {
        t.addCell(new Cell()
                .add(new Paragraph(etiqueta).setFontSize(8).setFontColor(GRIS))
                .add(new Paragraph(valor).setBold().setFontSize(16).setFontColor(color))
                .add(new Paragraph(nota).setFontSize(7).setFontColor(GRIS))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(color, 2))
                .setPadding(8).setPaddingLeft(10));
    }

    // ── 1. Cómo va el negocio ─────────────────────────────────────────

    private ReporteResumenAvanzadoDto comoVaElNegocio(Document doc, Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        titulo(doc, "1. Cómo va el negocio");

        ReporteResumenAvanzadoDto r = null;
        try {
            r = avanzado.resumenAvanzado(empresaId, desde, hasta);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin resumen de ventas: {}", e.getMessage());
        }
        if (r == null) {
            doc.add(nota("No hay ventas registradas en el período."));
            return null;
        }

        // La comparación contra el período anterior se grafica: un "+12%"
        // suelto no dice si el negocio creció desde mucho o desde poco.
        doc.add(GraficaPdf.comparativo(
                "Ventas este período", nzBd(r.getTotalVentasPeriodo()),
                pesos(r.getTotalVentasPeriodo()),
                "Período anterior", nzBd(r.getTotalVentasPeriodoAnterior()),
                pesos(r.getTotalVentasPeriodoAnterior()), AZUL));
        doc.add(nota(variacion(r.getVariacionVentas())));

        Table t = tabla(new float[]{40, 30, 30}, "Indicador", "Este período", "Comparación");
        fila(t, 0, "Ventas", pesos(r.getTotalVentasPeriodo()),
                variacion(r.getVariacionVentas()));
        fila(t, 1, "Compras", pesos(r.getTotalComprasPeriodo()), "—");
        fila(t, 2, "Margen bruto", pesos(r.getMargenBrutoPeriodo()), "—");
        fila(t, 3, "Transacciones", String.valueOf(nz(r.getCantidadTransacciones())),
                "Ticket promedio " + pesos(r.getTicketPromedio()));
        fila(t, 4, "Clientes nuevos", String.valueOf(nz(r.getClientesNuevos())),
                nz(r.getClientesRecurrentes()) + " recurrentes");
        doc.add(t);

        if (r.getTopProducto() != null || r.getTopCategoria() != null) {
            doc.add(nota("Lo que más se vendió: " + txt(r.getTopProducto())
                    + (r.getTopCategoria() != null ? "  ·  Categoría: " + r.getTopCategoria() : "")
                    + (r.getTopVendedor() != null ? "  ·  Vendedor: " + r.getTopVendedor() : "")));
        }
        return r;
    }

    // ── 2. Ventas que no se concretaron ───────────────────────────────

    /**
     * Carritos del POS que se armaron y se vaciaron sin vender. Es venta que
     * estuvo a punto de pasar: si un producto se abandona seguido, el precio o
     * la disponibilidad están espantando al cliente.
     */
    private void ventasPerdidas(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta,
            ReporteResumenAvanzadoDto ventas) {
        titulo(doc, "2. Ventas que no se concretaron");

        CarritoAbandonadoDtos.Reporte r;
        try {
            r = carritos.reporte(empresaId, desde, hasta, CarritoAbandonadoService.MINUTOS_POR_DEFECTO, null);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin carritos abandonados: {}", e.getMessage());
            doc.add(nota("No se pudo leer el registro de carritos abandonados."));
            return;
        }
        if (r == null || r.getCarritos() == 0) {
            doc.add(nota("No hubo carritos abandonados de " + CarritoAbandonadoService.MINUTOS_POR_DEFECTO
                    + " minutos o más en el período."
                    + (r != null && r.getDescartadosPorTiempo() > 0
                            ? " Se vaciaron " + r.getDescartadosPorTiempo()
                                    + " más rápido: se toman como correcciones del cajero." : "")));
            return;
        }

        Table t = tabla(new float[]{40, 25, 35}, "Indicador", "Valor", "Qué significa");
        fila(t, 0, "Carritos abandonados", String.valueOf(r.getCarritos()),
                r.getProductos() + " producto(s) que no se vendieron");
        String pctVentas = "—";
        if (ventas != null && nzBd(ventas.getTotalVentasPeriodo()).signum() > 0) {
            pctVentas = r.getValor().multiply(BigDecimal.valueOf(100))
                    .divide(ventas.getTotalVentasPeriodo(), 1, RoundingMode.HALF_UP) + "% de lo vendido";
        }
        fila(t, 1, "Valor sin vender", pesos(r.getValor()), pctVentas);
        fila(t, 2, "Tiempo promedio armado", r.getMinutosPromedio() + " min",
                "Lo que duró el carrito antes de vaciarse");
        doc.add(t);

        List<CarritoAbandonadoDtos.ProductoAbandonado> top = r.getTopProductos().stream().limit(6).toList();
        if (!top.isEmpty()) {
            doc.add(new Paragraph("Productos que más se abandonan").setFontSize(9).setBold().setMarginTop(8));
            doc.add(GraficaPdf.barras(top.stream()
                    .map(x -> new GraficaPdf.Barra(x.getNombre(), nzBd(x.getValor()),
                            pesos(x.getValor()) + " · " + x.getCarritos() + " carrito(s)"))
                    .toList(), AMBAR));
        }

        if (r.getPorCajero().size() > 1) {
            Table c = tabla(new float[]{50, 20, 30}, "Cajero", "Carritos", "Valor");
            for (int i = 0; i < r.getPorCajero().size(); i++) {
                var pc = r.getPorCajero().get(i);
                fila(c, i, txt(pc.getUsuario()), String.valueOf(pc.getCarritos()), pesos(pc.getValor()));
            }
            doc.add(c);
        }
        doc.add(nota("Se cuentan los carritos que estuvieron armados al menos "
                + CarritoAbandonadoService.MINUTOS_POR_DEFECTO + " minutos antes de vaciarse. Un producto "
                + "que se abandona seguido suele tener un precio que el cliente no acepta o no estar "
                + "disponible en la cantidad pedida."));
    }

    // ── 3. Cartera ────────────────────────────────────────────────────

    private void cartera(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta) {
        titulo(doc, "3. Lo que le deben y lo que debe");

        ReporteCarteraResumenDto cxc = carteraResumen(empresaId, ReporteCarteraFiltroDto.CXC);
        ReporteCarteraResumenDto cxp = carteraResumen(empresaId, ReporteCarteraFiltroDto.CXP);
        if (cxc == null && cxp == null) {
            doc.add(nota("No hay cartera pendiente."));
            return;
        }

        Table t = tabla(new float[]{22, 16, 16, 16, 15, 15},
                "", "Saldo", "Corriente", "1–30 días", "31–90 días", "+90 días");
        if (cxc != null) {
            filaCartera(t, 0, "Le deben (clientes)", cxc);
        }
        if (cxp != null) {
            filaCartera(t, 1, "Usted debe (proveedores)", cxp);
        }
        doc.add(t);

        // La composición por edades se grafica: la proporción de lo vencido
        // contra lo corriente es la lectura que importa, y en una tabla de
        // números hay que dividir mentalmente para verla.
        if (cxc != null && nzBd(cxc.getSaldoPendiente()).signum() > 0) {
            doc.add(new Paragraph("Composición de lo que le deben")
                    .setFontSize(9).setBold().setMarginTop(8));
            doc.add(GraficaPdf.barraApilada(List.of(
                    new GraficaPdf.Barra("Al día", nzBd(cxc.getCorriente()),
                            pesos(cxc.getCorriente()), VERDE),
                    new GraficaPdf.Barra("1–30 días", nzBd(cxc.getMora1a30()),
                            pesos(cxc.getMora1a30()), AMBAR),
                    new GraficaPdf.Barra("31–90 días", nzBd(cxc.getMora31a60())
                            .add(nzBd(cxc.getMora61a90())),
                            pesos(nzBd(cxc.getMora31a60()).add(nzBd(cxc.getMora61a90()))), AMBAR),
                    new GraficaPdf.Barra("+90 días", nzBd(cxc.getMoraMas90()),
                            pesos(cxc.getMoraMas90()), ROJO))));
        }

        // Lo vencido a más de 90 días es lo que difícilmente se recupera: se
        // dice aparte porque dentro del saldo total se pierde.
        if (cxc != null && nzBd(cxc.getMoraMas90()).signum() > 0) {
            doc.add(nota("De lo que le deben, " + pesos(cxc.getMoraMas90())
                    + " lleva más de 90 días vencido: es lo más difícil de recuperar."));
        }

        // Estados de cuenta: el saldo total no dice a quién llamar primero.
        estadosDeCuenta(doc, "Quién le debe más", "Cliente", cxc);
        estadosDeCuenta(doc, "A quién le debe más", "Proveedor", cxp);

        recaudo(doc, empresaId, desde, hasta);
    }

    /** Los cinco terceros con mayor saldo, con lo vencido separado. */
    private void estadosDeCuenta(Document doc, String titulo, String columna, ReporteCarteraResumenDto r) {
        if (r == null || r.getTerceros() == null || r.getTerceros().isEmpty()) {
            return;
        }
        List<ReporteCarteraTerceroDto> top = r.getTerceros().stream()
                .filter(x -> nzBd(x.getSaldoPendiente()).signum() > 0)
                .limit(5).toList();
        if (top.isEmpty()) {
            return;
        }
        doc.add(new Paragraph(titulo).setFontSize(9).setBold().setMarginTop(10));
        Table t = tabla(new float[]{38, 12, 18, 18, 14}, columna, "Facturas", "Saldo", "Vencido", "Mora máx.");
        for (int i = 0; i < top.size(); i++) {
            ReporteCarteraTerceroDto x = top.get(i);
            BigDecimal vencido = nzBd(x.getSaldoPendiente()).subtract(nzBd(x.getCorriente()));
            fila(t, i, txt(x.getTerceroNombre()), String.valueOf(x.getDocumentos() != null ? x.getDocumentos() : 0),
                    pesos(x.getSaldoPendiente()), pesos(vencido),
                    x.getDiasMoraMax() != null && x.getDiasMoraMax() > 0 ? x.getDiasMoraMax() + " días" : "Al día");
        }
        doc.add(t);
        if (r.getTerceros().size() > top.size()) {
            BigDecimal suma = top.stream().map(x -> nzBd(x.getSaldoPendiente())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = nzBd(r.getSaldoPendiente());
            if (total.signum() > 0) {
                doc.add(nota("Estos " + top.size() + " concentran el "
                        + suma.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP)
                        + "% del saldo (" + r.getTerceros().size() + " terceros en total)."));
            }
        }
    }

    /**
     * Lo que entró por cartera y lo que salió a proveedores en el período. Las
     * retenciones que practicaron los clientes se muestran aparte: bajaron la
     * cartera pero no son dinero, se recuperan en las declaraciones.
     */
    private void recaudo(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta) {
        List<ReporteCarteraQueryRepository.MedioRecaudo> cobros;
        List<ReporteCarteraQueryRepository.MedioRecaudo> pagos;
        try {
            cobros = carteraRepo.recaudoPorMedio(empresaId, ReporteCarteraFiltroDto.CXC, desde, hasta);
            pagos = carteraRepo.recaudoPorMedio(empresaId, ReporteCarteraFiltroDto.CXP, desde, hasta);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin recaudo: {}", e.getMessage());
            return;
        }
        if ((cobros == null || cobros.isEmpty()) && (pagos == null || pagos.isEmpty())) {
            return;
        }
        doc.add(new Paragraph("Lo que se cobró y se pagó en el período").setFontSize(9).setBold().setMarginTop(10));
        Table t = tabla(new float[]{46, 14, 20, 20}, "Medio", "Abonos", "Cobrado a clientes", "Pagado a proveedores");
        java.util.Map<String, BigDecimal[]> filas = new java.util.LinkedHashMap<>();
        if (cobros != null) {
            for (var m : cobros) {
                filas.computeIfAbsent(m.medio(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                filas.get(m.medio())[0] = filas.get(m.medio())[0].add(BigDecimal.valueOf(m.cantidad()));
                filas.get(m.medio())[1] = nzBd(m.total());
            }
        }
        if (pagos != null) {
            for (var m : pagos) {
                filas.computeIfAbsent(m.medio(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                filas.get(m.medio())[0] = filas.get(m.medio())[0].add(BigDecimal.valueOf(m.cantidad()));
                filas.get(m.medio())[2] = nzBd(m.total());
            }
        }
        BigDecimal retenciones = BigDecimal.ZERO;
        int i = 0;
        for (var e : filas.entrySet()) {
            boolean esRetencion = e.getKey().startsWith("RETE");
            if (esRetencion) retenciones = retenciones.add(e.getValue()[1]);
            fila(t, i++, etiquetaMedio(e.getKey()), e.getValue()[0].toPlainString(),
                    e.getValue()[1].signum() > 0 ? pesos(e.getValue()[1]) : "—",
                    e.getValue()[2].signum() > 0 ? pesos(e.getValue()[2]) : "—");
        }
        doc.add(t);
        if (retenciones.signum() > 0) {
            doc.add(nota("Los clientes le retuvieron " + pesos(retenciones) + ": bajó la cartera pero no "
                    + "entró como dinero. Se descuenta en las declaraciones de renta, IVA e ICA."));
        }
    }

    private String etiquetaMedio(String medio) {
        return switch (medio != null ? medio : "") {
            case "EFECTIVO" -> "Efectivo";
            case "TRANSFERENCIA" -> "Transferencia";
            case "TARJETA", "TARJETA_CREDITO", "TARJETA_DEBITO" -> "Tarjeta";
            case "NEQUI" -> "Nequi";
            case "DAVIPLATA" -> "Daviplata";
            case "CHEQUE" -> "Cheque";
            case "RETEFUENTE" -> "Retención en la fuente (se la practicaron)";
            case "RETEIVA" -> "ReteIVA (se la practicaron)";
            case "RETEICA" -> "ReteICA (se la practicaron)";
            default -> medio != null ? medio.charAt(0) + medio.substring(1).toLowerCase() : "—";
        };
    }

    private ReporteCarteraResumenDto carteraResumen(Integer empresaId, String tipo) {
        try {
            ReporteCarteraFiltroDto f = new ReporteCarteraFiltroDto();
            f.setTipo(tipo);
            f.setEstado("PENDIENTE");
            return carteraRepo.resumen(f, empresaId);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin cartera {}: {}", tipo, e.getMessage());
            return null;
        }
    }

    private void filaCartera(Table t, int i, String etiqueta, ReporteCarteraResumenDto c) {
        BigDecimal mora31a90 = nzBd(c.getMora31a60()).add(nzBd(c.getMora61a90()));
        fila(t, i, etiqueta,
                pesos(c.getSaldoPendiente()), pesos(c.getCorriente()),
                pesos(c.getMora1a30()), pesos(mora31a90), pesos(c.getMoraMas90()));
    }

    // ── 5. Gastos ─────────────────────────────────────────────────────

    private void gastos(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta) {
        titulo(doc, "4. En qué se fue la plata");

        ReporteGastosResumenDto g;
        try {
            ReporteGastosFiltroDto f = new ReporteGastosFiltroDto();
            f.setFechaDesde(desde);
            f.setFechaHasta(hasta);
            f.setAgrupacion(ReporteGastosFiltroDto.POR_CATEGORIA);
            g = gastosRepo.resumen(f, empresaId);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin gastos: {}", e.getMessage());
            return;
        }
        if (g == null || g.getLineas().isEmpty()) {
            doc.add(nota("No hay gastos registrados en el período."));
            return;
        }

        doc.add(new Paragraph("Total de gastos: " + pesos(g.getTotal())
                + "  ·  Deducible " + pesos(g.getTotalDeducible())
                + "  ·  No deducible " + pesos(g.getTotalNoDeducible()))
                .setFontSize(9).setMarginBottom(4));
        doc.add(nota("Lo no deducible baja la utilidad del negocio pero no el impuesto de renta. "
                + "Por eso van separados."));

        List<ReporteGastosLineaDto> top = g.getLineas().stream().limit(8).toList();

        // Las barras responden "¿en qué se me va la plata?" de un vistazo; la
        // tabla de abajo trae el detalle para quien necesite las cifras.
        doc.add(GraficaPdf.barras(top.stream()
                .map(l -> new GraficaPdf.Barra(l.getGrupo(), nzBd(l.getTotal()),
                        pesos(l.getTotal())))
                .toList(), AZUL));

        Table t = tabla(new float[]{45, 20, 20, 15}, "Categoría", "Total", "Deducible", "% del total");
        for (int i = 0; i < top.size(); i++) {
            ReporteGastosLineaDto l = top.get(i);
            fila(t, i, l.getGrupo(), pesos(l.getTotal()), pesos(l.getTotalDeducible()),
                    nzBd(l.getParticipacion()).setScale(1, RoundingMode.HALF_UP) + "%");
        }
        doc.add(t);
        if (g.getLineas().size() > top.size()) {
            doc.add(nota("Se muestran las " + top.size() + " categorías de mayor gasto de "
                    + g.getLineas().size() + "."));
        }
    }

    // ── 4. Inventario ─────────────────────────────────────────────────

    /**
     * Por dónde entra y por dónde se va la mercancía.
     *
     * <p>La pregunta que responde no es "cuánto inventario tengo" —eso lo dice
     * el módulo— sino "cuánto de lo que salió fue venta y cuánto se fue por
     * otro lado". Si las mermas pesan como las ventas, el problema no está en
     * el inventario sino en quién lo maneja.
     */
    private void inventario(Document doc, PdfDocument pdf, Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        titulo(doc, "5. Cómo se movió el inventario");

        List<MovimientoPorFamilia> familias;
        try {
            familias = kardexRepo.movimientoPorFamilia(empresaId, desde, hasta);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin movimiento de inventario: {}", e.getMessage());
            return;
        }
        if (familias == null || familias.isEmpty()) {
            doc.add(nota("No hubo movimientos de inventario en el período."));
            return;
        }

        // La torta responde "¿por dónde se fue la mercancía?". Se valoriza al
        // costo: comparar unidades de productos distintos no significa nada.
        List<GraficaPdf.Barra> salidas = familias.stream()
                .filter(f -> nzBd(f.valorSalidas()).signum() > 0)
                .map(f -> new GraficaPdf.Barra(etiquetaFamilia(f.familia()),
                        nzBd(f.valorSalidas()), pesos(f.valorSalidas())))
                .toList();
        if (!salidas.isEmpty()) {
            doc.add(new Paragraph("Por dónde salió la mercancía, valorizada al costo")
                    .setFontSize(9).setBold().setMarginTop(6));
            doc.add(GraficaPdf.torta(pdf, salidas, 110f));
        }

        // Las barras verticales enfrentan entradas contra salidas: lo negativo
        // cuelga bajo la línea del cero, que es como realmente ocurrió.
        List<GraficaPdf.Barra> neto = familias.stream()
                .filter(f -> nzBd(f.entradas()).signum() > 0 || nzBd(f.salidas()).signum() > 0)
                .limit(7)
                .map(f -> {
                    BigDecimal n = nzBd(f.entradas()).subtract(nzBd(f.salidas()));
                    return new GraficaPdf.Barra(etiquetaFamilia(f.familia()), n,
                            n.stripTrailingZeros().toPlainString(),
                            n.signum() >= 0 ? VERDE : ROJO);
                })
                .toList();
        if (!neto.isEmpty()) {
            doc.add(new Paragraph("Unidades netas por tipo de movimiento")
                    .setFontSize(9).setBold().setMarginTop(10));
            doc.add(GraficaPdf.barrasVerticales(pdf, neto, 70f, null));
            doc.add(nota("Sobre la línea, lo que entró; debajo, lo que salió."));
        }

        Table t = tabla(new float[]{28, 14, 14, 22, 22},
                "Tipo", "Entradas", "Salidas", "Valor entradas", "Valor salidas");
        for (int i = 0; i < familias.size(); i++) {
            MovimientoPorFamilia f = familias.get(i);
            fila(t, i, etiquetaFamilia(f.familia()),
                    nzBd(f.entradas()).stripTrailingZeros().toPlainString(),
                    nzBd(f.salidas()).stripTrailingZeros().toPlainString(),
                    pesos(f.valorEntradas()), pesos(f.valorSalidas()));
        }
        doc.add(t);

        // La merma como porcentaje de lo que salió es el número que un dueño
        // reconoce: en valor absoluto no sabe si es mucho o poco.
        BigDecimal totalSalidas = familias.stream().map(f -> nzBd(f.valorSalidas()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal merma = familias.stream()
                .filter(f -> "MERMAS".equals(f.familia()))
                .map(f -> nzBd(f.valorSalidas()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (merma.signum() > 0 && totalSalidas.signum() > 0) {
            BigDecimal pct = merma.multiply(BigDecimal.valueOf(100))
                    .divide(totalSalidas, 1, RoundingMode.HALF_UP);
            doc.add(nota("Las mermas fueron " + pesos(merma) + ": el " + pct
                    + "% de todo lo que salió del inventario."));
        }
    }

    /** Los códigos de familia en palabras del negocio. */
    private String etiquetaFamilia(String familia) {
        return switch (familia != null ? familia : "") {
            case "VENTAS" -> "Ventas";
            case "COMPRAS" -> "Compras";
            case "DEVOLUCIONES" -> "Devoluciones";
            case "MERMAS" -> "Mermas";
            case "OBSEQUIOS" -> "Obsequios";
            case "CONSUMOS_INTERNOS" -> "Consumo interno";
            case "TRASLADOS" -> "Traslados";
            case "ANULACIONES" -> "Anulaciones";
            case "RECONTEOS" -> "Ajustes de reconteo";
            default -> "Otros";
        };
    }

    // ── 6. Contabilidad ───────────────────────────────────────────────

    private void contabilidadSeccion(Document doc, Integer empresaId, LocalDate desde,
            LocalDate hasta) {
        titulo(doc, "6. Resultado y balance");

        EstadoResultadosDto er = null;
        BalanceGeneralDto bg = null;
        try {
            er = contabilidad.estadoResultados(empresaId, desde.toString(), hasta.toString(),
                    null, null, null);
            bg = contabilidad.balanceGeneral(empresaId, hasta.toString());
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin contabilidad: {}", e.getMessage());
        }
        if (er == null || bg == null) {
            doc.add(nota("La contabilidad todavía no está configurada para este período."));
            return;
        }

        Table t = tabla(new float[]{50, 25, 25}, "Concepto", "Valor", "Margen");
        fila(t, 0, "Ingresos", pesos(er.getTotalIngresos()), "—");
        fila(t, 1, "Costos", pesos(er.getTotalCostos()), "—");
        fila(t, 2, "Utilidad bruta", pesos(er.getUtilidadBruta()),
                nzBd(er.getMargenBruto()).setScale(1, RoundingMode.HALF_UP) + "%");
        fila(t, 3, "Gastos", pesos(er.getTotalGastos()), "—");
        fila(t, 4, "Utilidad neta", pesos(er.getUtilidadNeta()),
                nzBd(er.getMargenNeto()).setScale(1, RoundingMode.HALF_UP) + "%");
        doc.add(t);

        // De dónde salen los ingresos y a dónde se van los gastos: el estado de
        // resultados en dos cifras no dice nada; sus líneas sí.
        detalleResultado(doc, "Principales ingresos", er.getIngresos(), VERDE);
        detalleResultado(doc, "Principales gastos y costos",
                concatenar(er.getGastos(), er.getCostos()), AMBAR);

        BalanceGeneralDetalladoDto det = null;
        try {
            det = contabilidad.balanceGeneralDetallado(empresaId, hasta.toString());
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin balance detallado: {}", e.getMessage());
        }

        Table b = tabla(new float[]{50, 50}, "Balance a " + hasta.format(F_FECHA), "Valor");
        fila(b, 0, "Activo", pesos(bg.getTotalActivo()));
        fila(b, 1, "Pasivo", pesos(bg.getTotalPasivo()));
        fila(b, 2, "Patrimonio", pesos(bg.getTotalPatrimonio()));
        doc.add(b);

        doc.add(GraficaPdf.barraApilada(List.of(
                new GraficaPdf.Barra("Lo que debe", nzBd(bg.getTotalPasivo()),
                        pesos(bg.getTotalPasivo()), AMBAR),
                new GraficaPdf.Barra("Lo que es suyo", nzBd(bg.getTotalPatrimonio()),
                        pesos(bg.getTotalPatrimonio()), VERDE))));
        doc.add(nota("De cada peso que tiene el negocio, así se reparte entre deuda y "
                + "patrimonio propio."));

        indicadores(doc, er, bg, det);

        BigDecimal ec = nzBd(bg.getEcuacionContable());
        doc.add(nota(ec.abs().compareTo(new BigDecimal("0.01")) <= 0
                ? "El balance cierra: el activo es igual al pasivo más el patrimonio más el resultado."
                : "ATENCIÓN: el balance NO cierra por " + pesos(ec.abs())
                        + ". Ningún estado financiero que salga de él es confiable."));
    }

    // ── 7. Impuestos del período ──────────────────────────────────────

    /**
     * Lo que va a salir en las declaraciones, leído de la contabilidad. Es un
     * borrador con el rango del reporte: el IVA se declara por bimestre o
     * cuatrimestre, y la pantalla Declaraciones lo arma con el período exacto.
     */
    private void impuestos(Document doc, Integer empresaId, LocalDate desde, LocalDate hasta) {
        titulo(doc, "7. Impuestos del período");

        BorradorDeclaracionDto iva = null;
        BorradorDeclaracionDto ret = null;
        try {
            iva = declaraciones.iva(empresaId, desde, hasta);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin borrador de IVA: {}", e.getMessage());
        }
        try {
            ret = declaraciones.retencion(empresaId, desde, hasta);
        } catch (RuntimeException e) {
            log.warn("[Reporte gerencial] Sin borrador de retención: {}", e.getMessage());
        }
        if (iva == null && ret == null) {
            doc.add(nota("No se pudieron calcular los impuestos del período."));
            return;
        }

        Table t = tabla(new float[]{60, 40}, "Concepto", "Valor");
        int i = 0;
        if (iva != null) {
            fila(t, i++, "IVA generado en ventas", pesos(totalSeccion(iva, "IVA generado")));
            fila(t, i++, "Menos: IVA descontable en compras y gastos", pesos(totalSeccion(iva, "IVA descontable")));
            fila(t, i++, "Menos: ReteIVA que le practicaron", pesos(totalSeccion(iva, "Retenciones de IVA")));
            fila(t, i++, "IVA: " + iva.getResultadoEtiqueta().toLowerCase(), pesos(iva.getResultado().abs()));
        }
        if (ret != null) {
            fila(t, i++, "Retención en la fuente practicada (renta)", pesos(totalSeccion(ret, "Retención en la fuente")));
            fila(t, i++, "Retención a título de IVA practicada", pesos(totalSeccion(ret, "Retención a título de IVA")));
            fila(t, i++, "Retenciones a pagar (formulario 350)", pesos(ret.getResultado()));
        }
        doc.add(t);
        doc.add(nota("Borrador leído de la contabilidad para el rango de este reporte. El IVA se declara "
                + "por bimestre o cuatrimestre y la retención por mes: use Contabilidad → Declaraciones "
                + "con el período exacto antes de presentar."));

        // Las advertencias del borrador (asientos en borrador, INC mezclado con
        // IVA, descuadres) valen igual aquí: sin ellas la cifra engaña.
        java.util.LinkedHashSet<String> avisos = new java.util.LinkedHashSet<>();
        if (iva != null) avisos.addAll(iva.getAdvertencias());
        if (ret != null) avisos.addAll(ret.getAdvertencias());
        avisos.stream().filter(a -> !a.startsWith("Las retenciones se agrupan")).limit(4)
                .forEach(a -> doc.add(nota("Atención: " + a)));
    }

    private static BigDecimal totalSeccion(BorradorDeclaracionDto b, String prefijo) {
        return b.getSecciones().stream()
                .filter(s -> s.getTitulo() != null && s.getTitulo().startsWith(prefijo))
                .map(BorradorDeclaracionDto.Seccion::getTotal)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    /** Las líneas de mayor peso del estado de resultados, graficadas. */
    private void detalleResultado(Document doc, String titulo,
            List<EstadoResultadosLineaDto> lineas, DeviceRgb color) {
        if (lineas == null || lineas.isEmpty()) {
            return;
        }
        List<EstadoResultadosLineaDto> top = lineas.stream()
                .filter(l -> l.getSaldo() != null && l.getSaldo().signum() != 0)
                .sorted((a, b) -> b.getSaldo().abs().compareTo(a.getSaldo().abs()))
                .limit(6).toList();
        if (top.isEmpty()) {
            return;
        }
        doc.add(new Paragraph(titulo).setFontSize(9).setBold().setMarginTop(8));
        doc.add(GraficaPdf.barras(top.stream()
                .map(l -> new GraficaPdf.Barra(txt(l.getNombre()), l.getSaldo().abs(),
                        pesos(l.getSaldo())))
                .toList(), color));
    }

    private List<EstadoResultadosLineaDto> concatenar(List<EstadoResultadosLineaDto> a,
            List<EstadoResultadosLineaDto> b) {
        List<EstadoResultadosLineaDto> l = new java.util.ArrayList<>();
        if (a != null) l.addAll(a);
        if (b != null) l.addAll(b);
        return l;
    }

    /**
     * Los indicadores que responden "¿cómo está de salud el negocio?".
     *
     * <p>Cada uno viene con su lectura en lenguaje llano. Un número como "razón
     * corriente 1,4" no le dice nada a quien no es contador; "por cada peso que
     * debe a corto plazo tiene 1,4 para responder" sí.
     */
    private void indicadores(Document doc, EstadoResultadosDto er, BalanceGeneralDto bg,
            BalanceGeneralDetalladoDto det) {
        doc.add(new Paragraph("Indicadores").setFontSize(9).setBold().setMarginTop(10));

        Table t = tabla(new float[]{28, 14, 58}, "Indicador", "Valor", "Qué significa");
        int i = 0;

        if (det != null && nzBd(det.getTotalPasivoCorriente()).signum() > 0) {
            BigDecimal razon = nzBd(det.getTotalActivoCorriente())
                    .divide(det.getTotalPasivoCorriente(), 2, RoundingMode.HALF_UP);
            fila(t, i++, "Razón corriente", razon.toPlainString(),
                    razon.compareTo(BigDecimal.ONE) >= 0
                            ? "Por cada peso que debe a corto plazo tiene " + razon
                                    + " para responder."
                            : "Debe más a corto plazo de lo que tiene disponible: "
                                    + "puede quedarse sin con qué pagar.");
        }

        if (nzBd(bg.getTotalActivo()).signum() > 0) {
            BigDecimal endeuda = nzBd(bg.getTotalPasivo())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(bg.getTotalActivo(), 1, RoundingMode.HALF_UP);
            fila(t, i++, "Endeudamiento", endeuda + "%",
                    "De cada $100 que tiene el negocio, $" + endeuda + " son de terceros.");
        }

        if (er.getMargenBruto() != null) {
            BigDecimal mb = er.getMargenBruto().setScale(1, RoundingMode.HALF_UP);
            fila(t, i++, "Margen bruto", mb + "%",
                    "De cada $100 vendidos quedan $" + mb + " después de pagar la mercancía.");
        }

        if (er.getMargenNeto() != null) {
            BigDecimal mn = er.getMargenNeto().setScale(1, RoundingMode.HALF_UP);
            fila(t, i++, "Margen neto", mn + "%",
                    mn.signum() >= 0
                            ? "De cada $100 vendidos el negocio se queda con $" + mn + "."
                            : "El negocio está perdiendo $" + mn.abs()
                                    + " por cada $100 que vende.");
        }

        if (i == 0) {
            doc.add(nota("No hay suficientes datos contables para calcular indicadores."));
        } else {
            doc.add(t);
        }
    }

    // ── 7 y 8. Hallazgos ──────────────────────────────────────────────

    private void hallazgos(Document doc, AuditoriaResultadoDto a) {
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        titulo(doc, "8. Qué no cuadra");

        if (a.getHallazgos().isEmpty()) {
            doc.add(nota("No se encontró ningún descuadre en la operación del período."));
        } else {
            // Las causas son hipótesis ordenadas por frecuencia, no diagnósticos:
            // el sistema detecta el desacuerdo, no por qué ocurrió. Decirlo evita
            // que alguien tome la primera causa como un hecho probado.
            doc.add(nota("Debajo de cada hallazgo se listan las causas más frecuentes, "
                    + "ordenadas por probabilidad. Son puntos por dónde empezar a "
                    + "buscar, no un diagnóstico."));
            tablaHallazgos(doc, a.getHallazgos());
        }

        if (!a.getHeredados().isEmpty()) {
            titulo(doc, "9. Descuadres anteriores a las correcciones");
            doc.add(nota("Estos vienen de antes del período y ya están diagnosticados. "
                    + "Se listan para que quede constancia, no porque sean de esta operación."));
            tablaHallazgos(doc, a.getHeredados());
        }
    }

    private void tablaHallazgos(Document doc, List<HallazgoDto> lista) {
        Table t = tabla(new float[]{12, 40, 25, 23}, "Gravedad", "Qué pasa", "Cuánto", "Qué hacer");
        for (int i = 0; i < lista.size(); i++) {
            HallazgoDto h = lista.get(i);
            DeviceRgb fondo = (i % 2 == 0) ? BLANCO : ZEBRA;
            t.addCell(celda(etiqueta(h.getSeveridad()), fondo, false)
                    .setFontColor(color(h.getSeveridad())).setBold());
            t.addCell(new Cell()
                    .add(new Paragraph(h.getTitulo()).setBold().setFontSize(8))
                    .add(new Paragraph(h.getDescripcion()).setFontSize(7.5f).setFontColor(GRIS))
                    .setBackgroundColor(fondo).setBorder(Border.NO_BORDER).setPadding(5));
            // El monto es lo que mueve a alguien a actuar; la magnitud aparece
            // cuando la plata sola no cuenta la historia.
            t.addCell(new Cell()
                    .add(new Paragraph(h.getCantidad() + " caso(s)").setFontSize(8))
                    .add(new Paragraph(pesos(h.getMonto())).setBold().setFontSize(9))
                    .add(new Paragraph(h.getMagnitud() != null ? h.getMagnitud() : "")
                            .setFontSize(7.5f).setFontColor(GRIS))
                    .setBackgroundColor(fondo).setBorder(Border.NO_BORDER).setPadding(5));
            t.addCell(celda(h.getRecomendacion(), fondo, false).setFontSize(7.5f));
        }
        doc.add(t);

        // Las causas van debajo de la tabla y no en una columna: son varias por
        // hallazgo y adentro dejarían la fila ilegible.
        for (HallazgoDto h : lista) {
            if (h.getCausasProbables() == null || h.getCausasProbables().isEmpty()) {
                continue;
            }
            doc.add(new Paragraph("Por qué suele pasar: " + h.getTitulo())
                    .setFontSize(8.5f).setBold().setFontColor(AZUL).setMarginTop(8));
            for (String causa : h.getCausasProbables()) {
                doc.add(new Paragraph("•  " + causa)
                        .setFontSize(8).setMarginLeft(8).setMarginTop(1));
            }

            // La evidencia de otra área va junto a la causa que ayuda a
            // confirmar: un sobrante de caja y una merma del mismo turno no son
            // dos hallazgos, son uno con dos caras.
            if (h.getContexto() != null && !h.getContexto().isEmpty()) {
                doc.add(new Paragraph(h.getContextoTitulo() != null
                        ? h.getContextoTitulo() : "Evidencia relacionada")
                        .setFontSize(8).setBold().setMarginTop(6).setMarginLeft(8));
                Table ctx = tabla(new float[]{22, 14, 44, 20},
                        "Documento", "Fecha", "Qué pasó", "Valor");
                List<HallazgoDetalleDto> filas = h.getContexto();
                for (int i = 0; i < filas.size(); i++) {
                    HallazgoDetalleDto d = filas.get(i);
                    fila(ctx, i, txt(d.getReferencia()),
                            d.getFecha() != null ? d.getFecha().format(F_FECHA) : "—",
                            txt(d.getDescripcion()), pesos(d.getMonto()));
                }
                doc.add(ctx);
            }
        }
    }

    // ── Anexos ────────────────────────────────────────────────────────

    private void anexoMetodologia(Document doc, AuditoriaResultadoDto a) {
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        titulo(doc, "Anexo A · Cómo se obtuvo cada cifra");
        doc.add(nota("Un reporte de auditoría que no puede señalar de dónde sale un número no "
                + "se puede defender. Aquí está el cruce detrás de cada hallazgo."));

        Table t = tabla(new float[]{35, 65}, "Hallazgo", "Fuentes que se compararon");
        int i = 0;
        for (HallazgoDto h : todos(a)) {
            fila(t, i++, h.getTitulo(), h.getCruce());
        }
        if (i == 0) {
            doc.add(nota("No hubo hallazgos en este período."));
        } else {
            doc.add(t);
        }
    }

    private void anexoDetalle(Document doc, AuditoriaResultadoDto a) {
        List<HallazgoDto> conDetalle = todos(a).stream()
                .filter(h -> !h.getDetalle().isEmpty()).toList();
        if (conDetalle.isEmpty()) {
            return;
        }

        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        titulo(doc, "Anexo B · Documento por documento");

        for (HallazgoDto h : conDetalle) {
            doc.add(new Paragraph(h.getTitulo())
                    .setBold().setFontSize(10).setFontColor(AZUL).setMarginTop(10));
            Table t = tabla(new float[]{20, 15, 45, 20},
                    "Documento", "Fecha", "Detalle", "Monto");
            List<HallazgoDetalleDto> filas = h.getDetalle();
            for (int i = 0; i < filas.size(); i++) {
                HallazgoDetalleDto d = filas.get(i);
                fila(t, i, txt(d.getReferencia()),
                        d.getFecha() != null ? d.getFecha().format(F_FECHA) : "—",
                        txt(d.getDescripcion()), pesos(d.getMonto()));
            }
            doc.add(t);
            if (filas.size() >= 100) {
                doc.add(nota("Se muestran los primeros " + filas.size()
                        + " casos; puede haber más."));
            }
        }
    }

    private List<HallazgoDto> todos(AuditoriaResultadoDto a) {
        List<HallazgoDto> l = new java.util.ArrayList<>(a.getHallazgos());
        l.addAll(a.getHeredados());
        return l;
    }

    // ── Utilidades de maquetación ─────────────────────────────────────

    private void titulo(Document doc, String texto) {
        doc.add(new Paragraph(texto)
                .setBold().setFontSize(12).setFontColor(AZUL)
                .setMarginTop(16).setMarginBottom(4));
    }

    private Paragraph nota(String texto) {
        return new Paragraph(texto).setFontSize(8).setItalic()
                .setFontColor(GRIS).setMarginTop(4);
    }

    private void espacio(Document doc, float puntos) {
        doc.add(new Paragraph(" ").setFontSize(puntos / 2));
    }

    private Table tabla(float[] anchos, String... cabeceras) {
        Table t = new Table(UnitValue.createPercentArray(anchos))
                .useAllAvailableWidth().setMarginTop(6);
        for (String c : cabeceras) {
            t.addCell(new Cell()
                    .add(new Paragraph(c).setBold().setFontSize(8)
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(AZUL).setBorder(Border.NO_BORDER).setPadding(5));
        }
        return t;
    }

    /** Una fila con fondo alternado; la primera celda a la izquierda, el resto a la derecha. */
    private void fila(Table t, int i, String... valores) {
        DeviceRgb fondo = (i % 2 == 0) ? BLANCO : ZEBRA;
        for (int c = 0; c < valores.length; c++) {
            t.addCell(celda(valores[c], fondo, c > 0));
        }
    }

    private Cell celda(String valor, DeviceRgb fondo, boolean derecha) {
        return new Cell()
                .add(new Paragraph(valor != null ? valor : "—").setFontSize(8))
                .setBackgroundColor(fondo).setBorder(Border.NO_BORDER).setPadding(5)
                .setTextAlignment(derecha ? TextAlignment.RIGHT : TextAlignment.LEFT);
    }

    private String etiqueta(Severidad s) {
        if (s == Severidad.ALTA) return "GRAVE";
        if (s == Severidad.MEDIA) return "REVISAR";
        return "MENOR";
    }

    private DeviceRgb color(Severidad s) {
        if (s == Severidad.ALTA) return ROJO;
        if (s == Severidad.MEDIA) return AMBAR;
        return GRIS;
    }

    /**
     * Formato de miles con la configuración regional del servidor.
     *
     * <p>Es el mismo criterio de los Excel: un número sin separador no se lee.
     */
    private String pesos(BigDecimal v) {
        return String.format("$ %,.0f", nzBd(v));
    }

    private String variacion(BigDecimal v) {
        if (v == null) return "—";
        String signo = v.signum() >= 0 ? "▲ " : "▼ ";
        return signo + v.abs().setScale(1, RoundingMode.HALF_UP) + "% vs. período anterior";
    }

    private static BigDecimal nzBd(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static String txt(String v) {
        return v != null && !v.isBlank() ? v : "—";
    }
}
