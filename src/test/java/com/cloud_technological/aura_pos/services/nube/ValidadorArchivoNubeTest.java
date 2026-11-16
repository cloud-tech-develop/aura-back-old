package com.cloud_technological.aura_pos.services.nube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.utils.GlobalException;

class ValidadorArchivoNubeTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
    private static final byte[] EXE = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0, 0 };

    @Test
    void acepta_pdf_png_csv_y_xml() {
        assertEquals("application/pdf", ValidadorArchivoNube.validar("factura.pdf", PDF).mime());
        assertEquals("image/png", ValidadorArchivoNube.validar("logo.PNG", PNG).mime());
        assertEquals("text/csv", ValidadorArchivoNube.validar("terceros.csv", "nit;nombre\n1;A\n".getBytes()).mime());
        assertEquals("application/xml",
                ValidadorArchivoNube.validar("fe.xml", "<?xml version=\"1.0\"?><Invoice/>".getBytes()).mime());
    }

    @Test
    void acepta_xlsx_real() throws IOException {
        assertEquals("xlsx", ValidadorArchivoNube.validar("balance.xlsx", xlsx()).extension());
    }

    @Test
    void rechaza_ejecutable_disfrazado_de_pdf() {
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("factura.pdf", EXE));
    }

    @Test
    void rechaza_html_disfrazado_de_pdf_y_txt() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("factura.pdf", html));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("pagina.html", html));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("dibujo.svg", "<svg/>".getBytes()));
    }

    @Test
    void rechaza_xlsx_con_macros() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<workbook/>".getBytes());
            zip.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
            zip.write(new byte[] { 1, 2, 3 });
        }
        byte[] conMacros = out.toByteArray();
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("libro.xlsx", conMacros));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("libro.xlsm", conMacros));
    }

    @Test
    void rechaza_zip_y_doble_extension_peligrosa() {
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("todo.zip", PDF));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("factura.exe.pdf", PDF));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("factura.pdf.exe", EXE));
    }

    @Test
    void rechaza_mas_de_1_mb_y_acepta_justo_1_mb() {
        byte[] grande = new byte[1_048_577];
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("a.txt", grande));
        byte[] justo = new byte[1_048_576];
        java.util.Arrays.fill(justo, (byte) 'a');
        assertEquals("txt", ValidadorArchivoNube.validar("a.txt", justo).extension());
    }

    @Test
    void texto_con_bytes_nulos_no_es_texto() {
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("datos.csv", new byte[] { 'a', 0, 'b' }));
    }

    @Test
    void limpia_rutas_y_caracteres_engañosos() {
        assertEquals("x.pdf", ValidadorArchivoNube.limpiarNombre("../../otra_empresa/x.pdf"));
        assertEquals("x.pdf", ValidadorArchivoNube.limpiarNombre("C:\\fakepath\\x.pdf"));
        // U+202E invierte el texto: "factura‮fdp.exe" se ve como "facturaexe.pdf".
        assertEquals("facturafdp.exe", ValidadorArchivoNube.limpiarNombre("factura\u202Efdp.exe"));
        assertEquals("a_b_.pdf", ValidadorArchivoNube.limpiarNombre("a<b>.pdf"));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.limpiarNombre("CON.pdf"));
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.limpiarNombre("..."));
        assertEquals(150, ValidadorArchivoNube.limpiarNombre("a".repeat(300) + ".pdf").length());
    }

    @Test
    void el_truco_de_derecha_a_izquierda_queda_como_exe_y_se_rechaza() {
        assertThrows(GlobalException.class, () -> ValidadorArchivoNube.validar("factura\u202Efdp.exe", EXE));
    }

    private static byte[] xlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("ok");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
