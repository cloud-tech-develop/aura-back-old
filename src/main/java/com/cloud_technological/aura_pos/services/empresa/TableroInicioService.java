package com.cloud_technological.aura_pos.services.empresa;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.ChequeoDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.GrupoChequeoDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.PuestaEnMarchaDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.ResumenFinancieroDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.TableroComercialDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.TableroInicioQueryRepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Cifras de los tableros de inicio y lista de puesta en marcha
 * (docs/PLAN_PERFIL_EMPRESA.md, P5–P7). La lista se calcula cada vez: nunca se
 * guarda, así que no se desactualiza.
 */
@Service
public class TableroInicioService {

    private final TableroInicioQueryRepository query;
    private final EmpresaJPARepository empresaRepo;
    private final ConfiguracionEmpresaService configuracion;
    private final ConfiguracionContableService configuracionContable;

    public TableroInicioService(TableroInicioQueryRepository query, EmpresaJPARepository empresaRepo,
            ConfiguracionEmpresaService configuracion, ConfiguracionContableService configuracionContable) {
        this.query = query;
        this.empresaRepo = empresaRepo;
        this.configuracion = configuracion;
        this.configuracionContable = configuracionContable;
    }

    public ResumenFinancieroDto financiero(Integer empresaId) {
        return query.financiero(empresaId);
    }

    public TableroComercialDto comercial(Integer empresaId) {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        TableroComercialDto d = query.comercial(empresaId, hoy.withDayOfMonth(1), hoy);
        d.setFinanciero(query.financiero(empresaId));
        return d;
    }

    public PuestaEnMarchaDto puestaEnMarcha(Integer empresaId) {
        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        List<LineaUso> lineas = configuracion.lineas(empresa);

        PuestaEnMarchaDto out = new PuestaEnMarchaDto();
        // La contabilidad la necesitan todas; el grupo va una sola vez.
        out.getGrupos().add(contable(empresaId, lineas.contains(LineaUso.CONTABILIDAD)));
        if (lineas.contains(LineaUso.NOMINA)) out.getGrupos().add(nomina(empresaId));
        if (lineas.contains(LineaUso.POS) || lineas.contains(LineaUso.COMERCIAL))
            out.getGrupos().add(comercial(empresaId, empresa, lineas));

        int pendientes = 0;
        for (GrupoChequeoDto g : out.getGrupos())
            for (ChequeoDto c : g.getChequeos())
                if (!c.isOk()) pendientes++;
        out.setPendientes(pendientes);
        out.setCompleta(pendientes == 0);
        return out;
    }

    private GrupoChequeoDto contable(Integer empresaId, boolean completo) {
        GrupoChequeoDto g = grupo("CONTABILIDAD", "Contabilidad");
        long puc = query.cuentasPuc(empresaId);
        g.getChequeos().add(ChequeoDto.de("PUC", "Plan de cuentas cargado", puc > 0,
                puc > 0 ? puc + " cuentas" : "Sin plan de cuentas: cárguelo o impórtelo",
                "/contabilidad/plan-cuentas"));

        long sinCuenta = configuracionContable.listar(empresaId).stream()
                .filter(c -> c.getCuentaId() == null).count();
        g.getChequeos().add(ChequeoDto.de("PARAMETRIZACION", "Parametrización contable completa", sinCuenta == 0,
                sinCuenta == 0 ? "Todos los conceptos tienen cuenta"
                        : sinCuenta + " concepto(s) sin cuenta: esos movimientos no se contabilizan",
                "/contabilidad/parametrizacion"));

        long bancos = query.cuentasBancarias(empresaId);
        long bancosSinCuenta = query.cuentasBancariasSinCuentaContable(empresaId);
        g.getChequeos().add(ChequeoDto.de("BANCOS", "Cuentas bancarias con su cuenta contable",
                bancos > 0 && bancosSinCuenta == 0,
                bancos == 0 ? "No hay cuentas bancarias creadas"
                        : bancosSinCuenta == 0 ? bancos + " cuenta(s) bancaria(s)"
                                : bancosSinCuenta + " cuenta(s) bancaria(s) sin cuenta contable",
                "/tesoreria/cuentas-bancarias"));

        // Solo quien lleva la contabilidad en Aura arranca desde saldos iniciales.
        if (completo) {
            boolean saldos = query.tieneSaldosIniciales(empresaId);
            g.getChequeos().add(ChequeoDto.de("SALDOS_INICIALES", "Saldos iniciales cargados", saldos,
                    saldos ? "Hay asiento de apertura" : "Sin saldos iniciales: impórtelos desde Excel",
                    "/contabilidad/importar"));
        }
        return g;
    }

    private GrupoChequeoDto nomina(Integer empresaId) {
        GrupoChequeoDto g = grupo("NOMINA", "Nómina");
        boolean config = query.tieneConfigNomina(empresaId);
        g.getChequeos().add(ChequeoDto.de("CONFIG_NOMINA", "Configuración de nómina", config,
                config ? "Salario mínimo, auxilio y periodicidad definidos"
                        : "Defina salario mínimo, auxilio de transporte y periodicidad",
                "/nomina/config"));

        long contratos = query.contratosActivos(empresaId);
        g.getChequeos().add(ChequeoDto.de("EMPLEADOS", "Empleados con contrato", contratos > 0,
                contratos > 0 ? contratos + " contrato(s) activo(s)" : "No hay empleados con contrato activo",
                "/nomina/empleados"));

        long sinAfiliar = contratos > 0 ? query.contratosSinAfiliaciones(empresaId) : 0;
        g.getChequeos().add(ChequeoDto.de("AFILIACIONES", "EPS, pensión y caja asignadas",
                contratos > 0 && sinAfiliar == 0,
                contratos == 0 ? "Primero cree los contratos"
                        : sinAfiliar == 0 ? "Todos los contratos tienen sus afiliaciones"
                                : sinAfiliar + " contrato(s) sin EPS, pensión o caja vigente",
                "/nomina/empleados"));

        boolean aportante = query.tieneAportantePila(empresaId);
        g.getChequeos().add(ChequeoDto.de("APORTANTE_PILA", "Datos del aportante para PILA", aportante,
                aportante ? "Configurado" : "Falta tipo de aportante, actividad y representante legal",
                "/nomina/pila"));
        return g;
    }

    private GrupoChequeoDto comercial(Integer empresaId, EmpresaEntity empresa, List<LineaUso> lineas) {
        GrupoChequeoDto g = grupo(lineas.contains(LineaUso.COMERCIAL) ? "COMERCIAL" : "POS", "Ventas e inventario");
        long productos = query.productos(empresaId);
        g.getChequeos().add(ChequeoDto.de("PRODUCTOS", "Productos creados", productos > 0,
                productos > 0 ? productos + " producto(s)" : "No hay productos: créelos o impórtelos",
                "/catalogo/productos"));
        if (empresa.isFacturaElectronica()) {
            boolean rango = empresa.getFactusNumberingRangeId() != null;
            g.getChequeos().add(ChequeoDto.de("RANGO_FE", "Rango de facturación electrónica", rango,
                    rango ? "Rango asignado" : "Falta el rango de numeración de Factus",
                    null));
        }
        return g;
    }

    private static GrupoChequeoDto grupo(String linea, String nombre) {
        GrupoChequeoDto g = new GrupoChequeoDto();
        g.setLinea(linea);
        g.setNombre(nombre);
        return g;
    }
}
