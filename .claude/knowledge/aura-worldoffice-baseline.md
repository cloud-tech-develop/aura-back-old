# BASE FUNCIONAL COMPLETA — REFERENCIA WORLD OFFICE PARA AURA

> Objetivo: conservar el conocimiento funcional observado en los cuatro videos y convertirlo en una referencia de análisis.
> Esta base NO significa que AURA deba copiar la interfaz ni cada decisión de World Office.
> Los archivos `transcripts/video-01.txt` a `video-04.txt` contienen la transcripción íntegra y son la fuente de detalle final.

# 0. Taxonomía transversal

El sistema observado se comporta como ERP contable-operativo integrado.

Dominios identificados:
- seguridad y usuarios;
- dashboard;
- empresas/sucursales;
- terceros;
- productos/servicios/gastos/activos/diferidos;
- bodegas e inventario;
- precios y descuentos;
- compras;
- ventas;
- POS;
- cuentas por cobrar;
- cuentas por pagar;
- caja y bancos;
- contabilidad;
- impuestos;
- facturación/documento soporte y eventos electrónicos;
- activos fijos;
- producción simplificada;
- cierres;
- informes;
- importación/exportación;
- mensajería/documentos.

# 1. VIDEO 1 — PARAMETRIZACIÓN, SEGURIDAD, CONTABILIDAD, CATÁLOGO Y TERCEROS

## 1.1 Usuarios
- El usuario parte de un tercero marcado como empleado en el sistema observado.
- Se asigna empresa y rol.
- Recibe correo para definir contraseña.
- Después del primer acceso cambia a estado autenticado.
- AURA no está obligada a copiar la regla “todo usuario debe ser empleado”; puede separar Persona/Tercero, Empleado y Usuario.

## 1.2 Roles
Roles iniciales observados:
- facturador;
- administrador;
- API.
Se pueden crear roles por área/proceso.

## 1.3 Permisos gruesos
Controlan:
- módulos/paneles;
- documentos;
- informes;
- configuraciones.
Estados visuales:
- completo;
- parcial;
- sin acceso.
Un rol puede crear documentos sin consultar informes.

## 1.4 Permisos detallados
Sobre recursos ya autorizados:
- ver;
- crear;
- editar;
- eliminar;
- visibilidad de botones;
- visibilidad de campos;
- obligatoriedad/opcionalidad;
- valores predeterminados.
No se debe otorgar permiso detallado si no existe permiso de nivel superior.

## 1.5 Overrides por usuario
Usuarios con el mismo rol pueden tener parámetros diferentes:
- prefijo;
- sucursal;
- bodega;
- valores predeterminados.
Caso observado: dos usuarios del mismo rol usan prefijos distintos por sucursal.

## 1.6 Dashboard principal
Indicadores observados:
- variación de ventas;
- productos más vendidos;
- ventas por vendedor;
- comparación entre años;
- ventas por cliente;
- cotizaciones.
La visualización del dashboard puede estar protegida por permisos.

## 1.7 Módulos visibles en permisos
- compras;
- ventas;
- comisiones;
- contabilidad;
- empresas/personas;
- bancos;
- productos/servicios;
- seguridad;
- facturación electrónica;
- POS;
- eventos de recepción.

## 1.8 Motor/plantillas de contabilización
Existe un asistente para crear plantillas según:
- actividad económica;
- agrupación comercial/industrial/servicios;
- tarifa 19/5/0;
- cuenta de ingreso;
- cuenta de devoluciones;
- cuenta de compra/inventario;
- cuenta de costo de ventas.
Se generan variantes por tarifa.

## 1.9 Detalle de contabilización
Las reglas pueden variar por:
- tipo de documento;
- tipo de tercero/contribuyente;
- empresa/sucursal;
- forma de pago;
- plantilla/tarifa.
La partida contable puede editarse.
Se pueden eliminar cuentas/retenciones de una regla.

## 1.10 Replicación de contabilización
Puede copiarse:
- a otra empresa/sucursal;
- a una nueva forma de pago.
Caso observado: copiar la lógica “crédito” a ADDI/Sistecrédito y luego cambiar la cuenta de contrapartida.

## 1.11 Plan de cuentas
- PUC jerárquico;
- creación de auxiliares/subauxiliares;
- asociación de tercero;
- categorías contables;
- cuentas de tesorería.
Caso: crear una subauxiliar específica para deudores Sistecrédito.

## 1.12 ReteICA/retenciones
- Tarifa puede estar asociada al tercero.
- La plantilla puede tener una cuenta general.
- Si se quiere desglose contable especial por tarifa se puede usar contabilización avanzada.
- No asumir que esta decisión del video es la única correcta para AURA.

## 1.13 Formas de pago
Se pueden crear nuevas.
Atributos:
- nombre;
- documentos en los que aplica;
- grupo de forma de pago;
- posible manejo de cupo/crédito;
- mapeo electrónico.
Grupos observados:
- crédito;
- contado;
- bono;
- cheque;
- consignación;
- transferencias;
- billeteras.

## 1.14 Traslado de cuentas contables
Permite trasladar:
- movimiento;
- parametrización;
- parametrización + movimiento.
Filtros:
- empresa/sucursal;
- tipo de documento;
- rango documentos;
- rango fechas;
- todos.
No mueve documentos bloqueados.

## 1.15 Traslado/fusión de terceros
Permite consolidar movimientos del tercero incorrecto al correcto.
Usos:
- duplicados;
- errores de identificación;
- cierre/consolidación tributaria.
Restricción observada: documentos electrónicos reportados no deben reinterpretarse cambiando tercero contablemente.

## 1.16 Maestro unificado de productos/servicios
Clasificaciones observadas:
- producto;
- servicio;
- gasto;
- intangible;
- diferido;
- dotación;
- activo fijo.
Campos base:
- código;
- descripción;
- unidad;
- agrupación;
- IVA;
- plantilla contable;
- activo;
- centro de costos;
- utilidad estimada;
- internacional;
- código de barras;
- código fábrica;
- facturar sin existencias.

## 1.17 Inventario especializado
Opciones:
- serial;
- talla/color;
- lote;
- favorito POS;
- componente de otro producto/kit.

## 1.18 Bodegas por producto
Por producto+bodega:
- ubicación;
- mínimo;
- máximo;
- reorden;
- bodega principal;
- estado;
- imagen.
El producto puede limitarse a determinadas bodegas.

## 1.19 Listas de precios
Múltiples:
- mayorista;
- minorista;
- afiliado;
- particular;
- por financiación/forma de pago;
- moneda.
No asumir precio único en producto.

## 1.20 Descuentos por cantidad
Reglas por intervalos de cantidad.
Pueden coexistir descuentos de tercero y producto.
Debe definirse cómo se combinan.

## 1.21 Impuestos por producto
Además de IVA aparecen:
- ad valorem;
- impoconsumo;
- bolsa;
- impoconsumo distrital/nacional;
- saludables/ultraprocesados;
- bebidas azucaradas;
- ACPM;
- gasolina;
- otros.
Diseño recomendado: catálogo de impuestos + relación producto-impuesto.

## 1.22 Conversiones de unidad
Ejemplos:
- paca -> unidades;
- rollo -> metros;
- galón -> litros;
- bulto -> kilos.
Permite comprar y vender en unidades diferentes manteniendo equivalencia.

## 1.23 Kits / ficha técnica
Producto compuesto por componentes previamente creados.
La ficha técnica/receta define cantidades.
La entrada de producto terminado descarga componentes y carga producto final.

## 1.24 Facturar sin existencia
Para servicios es natural.
Para productos puede habilitarse.
Riesgo observado:
- inventario negativo;
- costo promedio/costo de ventas inconsistente.
Diseño recomendado para AURA:
- BLOQUEAR;
- ADVERTIR;
- PERMITIR.

## 1.25 Servicios
Comparten catálogo base.
Diferencias:
- no requieren stock físico;
- facturar sin existencia automático;
- listas de precios;
- impuestos;
- contabilización;
- conversión posible (anualidad/mes).

## 1.26 AIU
Para servicios de construcción/aseo/cafetería/vigilancia:
- administración;
- imprevistos;
- utilidad;
- porcentaje por componente;
- cuenta por componente;
- componente gravado;
- posible discriminación contable.

## 1.27 Activos fijos
Cada activo idealmente individual:
- código;
- placa;
- serial;
- marca;
- modelo;
- responsable;
- categoría;
- centro de costo;
- costo histórico;
- valor residual;
- depreciación.
No agrupar activos si se requiere baja/mantenimiento/depreciación individual.

## 1.28 Ficha de activo
- fecha compra;
- placa;
- serial;
- responsable;
- adición/reforma;
- activo padre;
- depreciable;
- método;
- costo histórico;
- residual;
- vida útil;
- inicio/fin;
- baja;
- factores;
- pólizas;
- mantenimientos.
Métodos observados:
- línea recta;
- suma de dígitos;
- reducción de saldos;
- unidades producidas.

## 1.29 Terceros
Maestro de empresas/personas.
Propiedades posibles:
- cliente;
- proveedor;
- empleado.
Información:
- tipo/número documento;
- nombre/razón social;
- direcciones;
- ciudad/barrio/zona;
- teléfonos;
- emails;
- forma pago predeterminada;
- plazo;
- descuento;
- vendedor/comprador;
- lista precios;
- cupo;
- tipo contribuyente;
- ICA;
- actividad;
- contactos;
- observaciones;
- imagen;
- datos personalizados.

## 1.30 Creación rápida DIAN
Desde identificación se consultan datos básicos.
No confundir con ficha completa de tercero.

## 1.31 Historial 360 del tercero
Permite consultar documentos relacionados:
- facturas;
- notas;
- remisiones;
- compras;
- devoluciones;
- movimientos.

# 2. VIDEO 2 — SALDOS INICIALES Y CICLO DE COMPRAS

## 2.1 Bloqueo de periodos
La fecha de bloqueo impide documentos iguales o anteriores a un corte.
Para registrar saldos históricos puede requerirse reabrir un periodo.

## 2.2 Saldos iniciales
Documento con pestañas:
- inventarios;
- activos fijos;
- diferidos;
- contabilidad.
Inventario:
- producto;
- bodega;
- unidad/conversión;
- cantidad;
- costo unitario;
- centro de costos;
- nota.
Activos:
- activo;
- cantidad;
- valor/costo histórico;
- centro de costos.
Contabilidad:
- caja;
- bancos;
- cuentas por cobrar;
- obligaciones;
- proveedores;
- impuestos;
- laborales;
- patrimonio;
- depreciación acumulada;
- resto del balance.

Regla:
- diferencia débito/crédito debe ser cero.
- no debe permitirse iniciar otro saldo inicial descuadrado.
Carga:
- manual;
- masiva por plantillas Excel separadas.

## 2.3 Cuentas por cobrar/pagar iniciales
Se pueden cargar consolidadas o detalladas por documento.
La referencia detallada mejora trazabilidad y aplicación posterior de pagos.

## 2.4 Balance de prueba
Se usa para validar que los saldos iniciales quedaron reflejados.
Filtros:
- fechas;
- rango cuentas;
- tercero;
- moneda;
- cancelación cuentas.

## 2.5 Orden de compra
Documento administrativo:
- NO mueve inventario;
- NO contabiliza;
- NO crea CxP.
Campos:
- empresa/sucursal;
- prefijo/consecutivo;
- proveedor;
- fechas;
- comprador;
- forma pago;
- moneda;
- referencia proveedor;
- concepto;
- detalle productos;
- bodega;
- unidad;
- cantidad;
- valor;
- descuento;
- impuestos;
- centro costo;
- campos personalizados;
- adjuntos;
- comentarios.
Acciones:
- imprimir;
- descargar;
- WhatsApp/email;
- anular/eliminar/replicar según permisos;
- generar factura;
- generar anticipo/egreso;
- documentos derivados.

## 2.6 Remisión de compra
Uso:
- recibe mercancía antes de causación/factura.
Efectos:
- SÍ aumenta inventario;
- NO crea todavía CxP/contabilización de factura.
Permite cruzar parcialmente orden.
Debe conservar pendiente restante.

## 2.7 Factura de compra
Puede:
- partir de documentos previos;
- cruzar remisiones;
- cruzar pendientes de orden;
- agregar ítems nuevos.
Regla crítica:
- legalizar una remisión no debe volver a ingresar inventario por la cantidad ya recibida.
- solo la parte nueva afecta stock.
Efectos:
- contabilización;
- CxP;
- inventario según cantidad pendiente/no recibida;
- impuestos/retenciones según plantilla/tercero.

## 2.8 Documento soporte electrónico
En compras a no obligados a facturar electrónicamente se usa prefijo/resolución correspondiente.
Debe existir manejo de:
- resolución;
- prefijo electrónico;
- documentos pendientes;
- envío individual/masivo.
El video refleja normativa del momento; verificar DIAN vigente.

## 2.9 Nota crédito de compra — devolución
- disminuye CxP;
- devuelve cantidades;
- revierte inventario;
- reversa contabilización proporcional;
- cantidad editable;
- precio no debe alterarse arbitrariamente porque reversa causación original.
Motivos observados:
- devolución parcial;
- no aceptación;
- ajuste/anulación/otros según documento.

## 2.10 Nota crédito de compra — descuento
- disminuye CxP;
- NO mueve cantidad;
- afecta valor;
- revierte proporcionalmente cuentas/impuestos según configuración.

## 2.11 Nota débito de compra
- incrementa CxP;
- NO aumenta inventario por sí sola;
- afecta valores/contabilidad.
Si llegan nuevas unidades deben documentarse mediante nueva compra/remisión/factura, no con ND.

## 2.12 Documentos relacionados
Desde una factura se ve su cadena:
- NC devolución;
- NC descuento;
- ND;
- egresos;
- anticipos.

## 2.13 Anticipos
La factura puede cruzar anticipos previos.
Debe existir saldo aplicable y trazabilidad del cruce.

## 2.14 Comprobante de egreso
Puede generarse desde factura/CxP.
Selecciona:
- fuente del pago (caja/banco);
- valor total o parcial.
Reduce saldo pendiente.
Permite abonos parciales.

## 2.15 Gastos y servicios
Servicios/gastos pueden registrarse directamente como factura de compra.
La plantilla contable determina:
- gasto;
- IVA;
- retenciones;
- cuenta por pagar.
Gastos recurrentes pueden replicarse/copiase a nuevas fechas.

## 2.16 Nota de contabilidad / caja menor
Se muestra como alternativa para:
- reembolsos;
- gastos pagados por colaborador;
- caja menor;
- causaciones manuales.
Debe respetarse el criterio contable de la empresa.

## 2.17 Vista previa de contabilización
Antes de guardar un documento puede consultarse qué contabilización generará.
Se puede modificar regla o contabilización avanzada según permiso.

## 2.18 Reportes de compras
Capacidades observadas:
- por centro de costos;
- por forma de pago;
- comparativo mensual por comprador;
- proveedor agrupado por comprador;
- por documento;
- producto detallado por documento;
- por zonas.
Filtros:
- empresa;
- fechas;
- documento;
- proveedor;
- comprador;
- grupo inventario;
- centro costo;
- moneda;
- forma pago;
- producto;
- zona;
- moneda extranjera.
Salida:
- PDF;
- Excel.
Reportes largos:
- procesamiento y notificación;
- envío al correo.

## 2.19 Recepción de facturación electrónica
Carga de XML de proveedor:
- masiva o individual.
Datos:
- fecha;
- número;
- total;
- forma pago;
- proveedor;
- CUFE;
- estado.
Eventos se generan por factura.
Flujo observado:
- sin eventos;
- acuse;
- aceptación del bien/servicio;
- demás eventos aplicables.
Existe tercero/responsable aprobador.
La regla temporal/normativa debe verificarse contra DIAN vigente.

## 2.20 “Smart create”
Desde un documento se puede crear sin abandonar el flujo:
- tercero;
- dirección;
- forma de pago;
- comprador;
- producto;
- unidad;
- campo personalizado;
- prefijo.
Es una referencia importante de UX.

# 3. VIDEO 3 — CICLO DE VENTAS Y FACTURACIÓN

## 3.1 Cotización
Documento administrativo:
- NO inventario;
- NO contabilidad;
- NO CxC.
Encabezado:
- empresa/sucursal;
- prefijo;
- cliente;
- dirección;
- fechas;
- vencimiento;
- vendedor;
- moneda;
- forma pago;
- concepto.
Detalle:
- producto;
- bodega;
- disponibilidad;
- unidad;
- cantidad;
- lista/precio;
- descuento;
- impuestos;
- centro costo;
- notas;
- campos personalizados.
Acciones:
- imprimir;
- PDF;
- WhatsApp/email;
- anular/eliminar/replicar;
- generar factura/pedido.

## 3.2 Pedido
Documento administrativo.
No mueve stock por sí mismo.
Puede:
- partir de cotización;
- agregar nuevos ítems;
- convertirse en factura;
- recibir abonos/recibos de caja.
Caso de uso:
- plan separe.
Debe existir seguimiento de:
- pedido;
- abonos;
- saldo;
- facturación final.
El inventario físico sigue existiendo mientras no se despache; AURA debe decidir si además reserva disponibilidad.

## 3.3 Políticas de precio/descuento
Configurables:
- lista de precios;
- último precio facturado;
- costo + utilidad;
- descuento cliente;
- límite máximo;
- cliente+producto;
- cantidad vendida.

## 3.4 Remisión de venta
Representa despacho.
Efectos:
- SÍ disminuye inventario;
- NO crea CxC todavía.
Puede:
- cruzar pedido;
- agregar ítems;
- generar factura;
- generar devolución de remisión.

## 3.5 Devolución de remisión de venta
Revierte unidades despachadas antes de facturación.
Aumenta inventario.
No debe crear una nota fiscal si todavía no hubo factura.

## 3.6 Factura de venta
Puede:
- jalar remisiones/pedidos;
- agregar ítems nuevos;
- verificar existencias;
- aplicar precios/descuentos;
- manejar crédito/contado;
- generar CxC;
- contabilizar ingreso/impuestos/retenciones;
- afectar inventario solo por lo aún no afectado por remisión.
Regla crítica: evitar doble descarga.

## 3.7 Factura de servicio
No requiere stock físico.
Puede manejar:
- plazo;
- listas;
- impuestos;
- centros de costo;
- ingresos para terceros;
- campos sectoriales.

## 3.8 Ingresos para terceros
Parte del valor facturado corresponde a otro tercero.
Se configura:
- tercero beneficiario;
- valor o porcentaje;
- cuenta específica.
Casos:
- inmobiliarias;
- administración/intermediación;
- transportes.

## 3.9 Nota crédito venta — devolución
- disminuye CxC;
- devuelve inventario;
- cantidad sí cambia;
- valor ligado a origen.

## 3.10 Nota crédito venta — descuento
- disminuye CxC;
- no cambia inventario;
- cambia valor;
- reversa ingreso/impuesto proporcional según regla.

## 3.11 Nota débito venta
Incrementa CxC por mayor valor.
No mueve inventario por sí sola.

## 3.12 Recibo de caja
Aplica recaudos:
- caja;
- banco;
- otros medios.
Puede:
- cancelar factura;
- abonar parcialmente;
- cruzar varias facturas.
Al cancelar, el estado de cuenta deja solo pendientes.

## 3.13 Retenciones al recaudo
El video muestra que una retención esperada puede quitarse de la factura y reconocerse al recibo de caja si la empresa solo conoce la retención al momento del pago.
Debe ser parametrizable y no hardcodeado.

## 3.14 Facturación electrónica
Requiere:
- habilitación;
- resolución/prefijo vigente;
- envío individual o desde pendientes;
- estado DIAN;
- fecha presentación;
- seguimiento.
El sistema puede tener documentos contables/inventario antes del envío fiscal.
Las reglas de fecha y oportunidad mencionadas en el video deben verificarse con normativa actual.

## 3.15 Campos XML DIAN personalizados
Campos personalizados pueden mapearse a campos del XML:
- orden reference;
- orden compra;
- número despacho;
- remisión;
- tipo documento;
- línea de negocio;
- otros.

## 3.16 Facturación moneda extranjera
- catálogo de monedas;
- TRM automática observada;
- TRM editable;
- documento en moneda extranjera;
- contabilización en moneda local;
- bimoneda.
Limitación observada:
- diferencia en cambio no automática en esa versión.
Para AURA se debe decidir si se implementa motor de diferencia en cambio.

## 3.17 Exportación internacional
Marcación de factura como exportación.
Activa campos requeridos para operación exterior.
Productos pueden requerir código internacional.

## 3.18 Envío WhatsApp
Vinculación mediante QR a una línea.
Documentos pueden enviarse a:
- móvil del tercero;
- otro número.
Limitación observada:
- una línea vinculada a la vez.
AURA puede diseñar integración más robusta.

## 3.19 Smart create en ventas
Desde factura se crean:
- cliente rápido;
- cliente completo;
- nueva dirección;
- forma de pago;
- vendedor;
- producto;
- clasificación;
- contacto.

## 3.20 Comisiones
Esquemas observados:
- por recaudo;
- por ventas con porcentaje fijo;
- intervalos de valores;
- intervalos de porcentajes;
- por forma de pago.
Incluye:
- vendedor/empleado;
- meta;
- rangos;
- porcentaje/valor.
En recaudo se registra responsable del recaudo.

## 3.21 Reportes de ventas
- rentabilidad por producto/documento;
- comprobante diario;
- ventas agrupadas por centro de costo;
- agrupadas por forma de pago;
- comparativo mensual por vendedor;
- cliente agrupado por vendedor;
- cliente agrupado por zona;
- por documento;
- producto detallado por documento;
- vendedor agrupado por producto.
Filtros amplios:
- fechas;
- documentos;
- vendedor;
- cliente;
- grupo inventario;
- centro costo;
- forma pago;
- moneda;
- producto;
- zona.
Salidas:
- PDF;
- Excel.
Reportes grandes pueden enviarse por correo.

## 3.22 Importación/exportación de documentos
Exportar:
- registros filtrados a Excel con encabezado/detalle/extensiones.
Importar:
- plantillas Excel;
- ayudas por columna;
- validación en pasos;
- carga masiva.
Documentos mencionados:
- facturas;
- pedidos;
- cotizaciones;
- remisiones;
- recibos;
- egresos.

## 3.23 Venta de activo fijo
Caso excepcional:
- factura de venta;
- cuenta por cobrar;
- baja costo histórico;
- reverso depreciación acumulada;
- reconocimiento de utilidad/pérdida;
- asociación al activo para que deje de depreciarse.
Debe diseñarse como proceso especializado, no como venta normal.

# 4. VIDEO 4 — CIERRES, BANCOS, INVENTARIO, PRODUCCIÓN E INFORMES

## 4.1 Cierre mensual
No es un solo botón necesariamente.
Procesos observados:
- depreciación;
- costo de ventas;
- conciliación bancaria;
- amortización de diferidos;
- otros ajustes.

## 4.2 Depreciación
Automática o manual.
Automática:
- periodo;
- empresa/sucursal;
- activos parametrizados;
- cuenta depreciación acumulada vs gasto/costo;
- centro de costos.
Debe validar activos incluidos.
Manual sirve para casos especiales (p.ej. depreciaciones no estándar).

## 4.3 Costo de ventas
Automático o manual.
Automático:
- toma ventas del periodo;
- costo promedio;
- acredita inventario (clase 14);
- debita costo (clase 6);
- respeta centro de costos.
Incluye informe detallado:
- producto;
- cantidad;
- venta;
- costo;
- utilidad;
- margen.

## 4.4 Cancelación de cuentas / cierre anual
Cancela:
- ingresos;
- gastos;
- costos.
Traslada utilidad/pérdida al balance.
Normalmente proceso anual; la periodicidad es decisión contable.

## 4.5 Conciliación bancaria manual
Parámetros:
- empresa;
- periodo;
- cuenta;
- verificados/no verificados/todos.
Proceso:
- comparar contra extracto;
- marcar conciliado;
- crear gastos/notas bancarias;
- trasladar no verificados al siguiente periodo como partidas conciliatorias.

## 4.6 Gestión de bancos
Entidades y cuentas:
- banco;
- cuenta ahorro/corriente/tarjeta;
- tercero;
- cuenta contable;
- prefijo;
- empresa/sucursal;
- centro costo;
- activo.

## 4.7 Conciliación automática
- seleccionar banco/cuenta;
- periodo;
- cargar extracto;
- formato observado PDF;
- cruce automático;
- parametrización de gravamen/IVA/comisiones/gastos.
AURA debe diseñar parsers/conectores robustos y conciliación explicable.

## 4.8 Diferidos
Amortización automática desde nota contable según configuración del diferido.

## 4.9 Diferencia en cambio
En la versión observada no estaba automatizada.
Se hacía mediante nota contable/manual.
Para AURA: oportunidad de mejora si se maneja moneda extranjera.

## 4.10 Salida de almacén
Motivos observados:
- a costo/consumo;
- producto en proceso;
- cambio;
- deterioro;
- muestra;
- pérdida;
- traslado.
Debe existir motivo como entidad/regla, no ifs dispersos.

Efectos contables dependen del motivo:
- traslado: inventario contra inventario;
- deterioro/pérdida: inventario contra gasto/costo;
- proceso: materia prima -> WIP;
- consumo: inventario -> costo/gasto.

## 4.11 Entrada de almacén
Motivos observados:
- cambio;
- obsequio;
- sobrante;
- traslado;
- otros según operación.
Contabilización depende del motivo.

## 4.12 Traslado entre bodegas
Flujo observado:
1. salida por traslado desde origen;
2. entrada por traslado a destino.
Costo:
- costo promedio;
- no editable arbitrariamente.
Valor contable neto empresa = 0.
AURA puede implementar documento de transferencia único internamente generando dos movimientos atómicos.

## 4.13 Cambio de mercancía
Flujo:
1. entrada del producto devuelto;
2. salida del producto de reemplazo.
Debe conservar relación entre ambos movimientos.

## 4.14 Producción simplificada
Entrada de producto terminado:
- manual;
- según ventas;
- por negativos.
Receta:
- materia prima;
- cantidades;
- servicios externos.
Al fabricar:
- descarga componentes;
- calcula costo por suma/costo promedio;
- mueve materia prima -> proceso -> terminado;
- carga existencias del producto final.
No contempla necesariamente producción industrial completa/CIF.

## 4.15 Producción por ventas/negativos
Puede compensar productos vendidos sin existencia:
- identifica negativos;
- genera producción/entrada.
AURA debe manejarlo con controles para no ocultar errores de inventario.

## 4.16 Ajuste de inventario
Manual o generación automática de conteo.
Campos:
- empresa;
- prefijo;
- responsable;
- concepto;
- producto;
- bodega;
- cantidad sistema;
- cantidad física;
- diferencia;
- costo.
Resultado:
- sobrante -> entrada;
- faltante -> salida.
Debe generar trazabilidad y contabilización por motivo.

## 4.17 Informes contables
Observados:
- balance de prueba;
- certificados de impuestos;
- comprobantes diarios;
- balance general;
- balance general comparativo meses;
- comparativo entre años;
- estado de situación financiera/NIF según parametrización;
- estado de resultados;
- estado de resultados por centro de costos;
- ejecutivo por centro;
- comparativos;
- estados/cuentas por tercero;
- movimientos;
- saldos;
- vencimientos;
- periodo determinado;
- gestión de recaudo/cartera.
Salida:
- PDF;
- Excel.

## 4.18 Centros de costo
Usados transversalmente:
- productos;
- compras;
- ventas;
- activos;
- bancos;
- costo venta;
- reportes;
- estado resultados.

## 4.19 Informes de activos fijos
- por centro de costos;
- ubicación;
- responsable;
- categoría;
- vencimiento de garantía;
- mantenimiento;
- póliza;
- depreciación mensual/anual.
Datos:
- nombre;
- placa;
- serial;
- responsable;
- costo;
- depreciación acumulada.

## 4.20 Consolidado de existencias
Opciones:
- por bodega;
- por producto;
- producto detallado por bodega;
- talla/color;
- serial;
- lotes;
- conteos físicos.
Filtros:
- saldo positivo;
- negativo;
- sin saldo;
- bodegas;
- producto;
- grupo/clasificación.

## 4.21 Movimientos de inventario
Informes:
- por producto;
- por bodega;
- producto detallado por bodega.
Muestran:
- entradas;
- salidas;
- saldo.

## 4.22 Control de cruces
Permite revisar trazabilidad y pendientes:
Ventas:
cotización -> pedido -> remisión -> factura.
Compras:
orden -> remisión compra -> factura compra.
Consulta:
- cruzados;
- no cruzados;
- remisiones pendientes de facturar;
- pedidos pendientes de despachar/facturar.

## 4.23 Fichas técnicas
Reporta recetas/componentes.

## 4.24 Catálogo de inventario
Lista:
- productos;
- estado;
- grupo;
- clasificación;
- imagen miniatura opcional.

## 4.25 Kardex
Variantes:
- producto;
- bodega;
- talla/color;
- serial;
- lote.
Muestra cronológicamente movimientos y efectos de:
- saldos iniciales;
- compras;
- remisiones;
- ventas;
- devoluciones;
- notas;
- traslados;
- ajustes;
- producción.

## 4.26 Informe de precios
Compara producto contra varias listas de precios y existencias.
Puede mostrar hasta un conjunto de listas seleccionadas.

## 4.27 Máximos/mínimos/reorden
Existe reporte/control de:
- mínimo;
- máximo;
- punto de reorden.

## 4.28 Costos de producto terminado
Informe relaciona:
- entrada producto terminado;
- salida de componentes;
- fecha;
- cantidad;
- costo.

# 5. MATRIZ DE EFECTOS DOCUMENTALES

## Compras
| Documento | Inventario | Contabilidad | CxP |
|---|---:|---:|---:|
| Orden compra | No | No | No |
| Remisión compra | Sí + | No factura | No |
| Factura compra directa | Sí + | Sí | Sí |
| Factura legalizando remisión | Solo pendiente | Sí | Sí |
| NC devolución compra | Sí - | Sí reverso | Reduce |
| NC descuento compra | No cantidad | Sí | Reduce |
| ND compra | No cantidad | Sí | Aumenta |
| Egreso | No | Sí | Reduce/paga |

## Ventas
| Documento | Inventario | Contabilidad | CxC |
|---|---:|---:|---:|
| Cotización | No | No | No |
| Pedido | No | No | No |
| Remisión venta | Sí - | No factura | No |
| Devolución remisión | Sí + | No factura | No |
| Factura directa | Sí - | Sí | Sí |
| Factura legalizando remisión | Solo pendiente | Sí | Sí |
| NC devolución venta | Sí + | Sí reverso | Reduce |
| NC descuento venta | No cantidad | Sí | Reduce |
| ND venta | No cantidad | Sí | Aumenta |
| Recibo caja | No | Sí | Reduce/paga |

Esta matriz es esencial para detectar dobles afectaciones.

# 6. CAPACIDADES DE UX TRANSVERSALES OBSERVADAS

- buscador global para navegar a funcionalidades;
- favoritos;
- creación rápida sin salir del documento;
- campos personalizados;
- clasificaciones;
- adjuntos;
- comentarios internos con autor;
- notas imprimibles por ítem;
- vista preliminar;
- plantillas de impresión;
- PDF;
- email;
- WhatsApp;
- importar Excel;
- exportar Excel;
- notificaciones de procesos largos;
- histórico de movimientos;
- documentos relacionados;
- filtros amplios;
- vistas preliminares de contabilización;
- acciones contextuales desde el documento.

# 7. MODELO OBJETIVO SUGERIDO PARA AURA (A VALIDAR CONTRA CÓDIGO)

Entidades/capacidades candidatas:
- Tenant/Empresa
- Sucursal
- Bodega
- Caja
- CentroCosto
- Tercero
- TerceroRol
- Usuario
- Rol
- Permiso
- UserScope/UserOverride
- Producto
- ProductoVariante
- ProductoBodega
- Lote
- Serial
- UnidadMedida
- ConversionUnidad
- ListaPrecio
- ProductoPrecio
- ReglaDescuento
- Impuesto
- ProductoImpuesto
- FormaPagoGrupo
- FormaPago
- Documento
- DocumentoLinea
- DocumentoRelacion
- AplicacionDocumento
- MovimientoInventario
- SaldoInventario
- CuentaContable
- ReglaContable
- Asiento
- AsientoLinea
- CuentaCobrar
- CuentaPagar
- AplicacionPago
- Banco
- CuentaBancaria
- Conciliacion
- ActivoFijo
- Depreciacion
- MantenimientoActivo
- PolizaActivo
- Receta/FichaTecnica
- RecetaComponente
- OrdenProduccionSimplificada
- AuditoriaEvento

No implementar estas entidades por nombre sin revisar el modelo actual.

# 8. CHECKLIST DE AUDITORÍA DE AURA

El agente debe verificar uno por uno:

SEGURIDAD
[ ] roles
[ ] permisos por módulo
[ ] permisos por acción
[ ] permisos de campo
[ ] scope empresa/sucursal
[ ] override usuario
[ ] auditoría

EMPRESA
[ ] multiempresa
[ ] sucursales
[ ] prefijos
[ ] consecutivos
[ ] centros costo

TERCEROS
[ ] cliente/proveedor/empleado coexistente
[ ] múltiples direcciones
[ ] contactos
[ ] condiciones comerciales
[ ] crédito
[ ] impuestos
[ ] historial 360
[ ] fusión de duplicados

CATÁLOGO
[ ] producto
[ ] servicio
[ ] gasto
[ ] activo
[ ] conversiones
[ ] listas precios
[ ] impuestos múltiples
[ ] descuentos
[ ] serial/lote/talla/color
[ ] kit/receta

INVENTARIO
[ ] por bodega
[ ] costos
[ ] costo promedio
[ ] stock mínimo/máximo/reorden
[ ] negativos
[ ] traslados
[ ] cambios
[ ] deterioro/pérdida/muestra
[ ] ajuste físico
[ ] kardex
[ ] concurrencia

COMPRAS
[ ] orden
[ ] remisión
[ ] factura
[ ] cruce parcial
[ ] NC devolución
[ ] NC descuento
[ ] ND
[ ] anticipos
[ ] egresos
[ ] CxP
[ ] documento soporte
[ ] XML/eventos

VENTAS
[ ] cotización
[ ] pedido
[ ] plan separe
[ ] remisión
[ ] devolución remisión
[ ] factura
[ ] NC devolución
[ ] NC descuento
[ ] ND
[ ] recibos
[ ] CxC
[ ] electrónica
[ ] ingresos terceros
[ ] comisiones
[ ] moneda extranjera

CONTABILIDAD
[ ] PUC
[ ] motor reglas
[ ] asientos
[ ] contabilización avanzada
[ ] saldos iniciales
[ ] reclasificaciones
[ ] cierres
[ ] costo ventas
[ ] depreciación
[ ] diferidos
[ ] balance
[ ] resultados

BANCOS
[ ] entidades/cuentas
[ ] movimientos
[ ] conciliación manual
[ ] automática
[ ] partidas conciliatorias

ACTIVOS
[ ] ficha
[ ] responsable
[ ] centro costo
[ ] depreciación
[ ] mantenimiento
[ ] póliza
[ ] baja/venta

REPORTES
[ ] compras
[ ] ventas
[ ] inventarios
[ ] cartera
[ ] proveedores
[ ] contables
[ ] activos
[ ] exportación
[ ] procesamiento asíncrono

INTEGRACIONES
[ ] DIAN
[ ] email
[ ] WhatsApp
[ ] importación Excel
[ ] exportación Excel/PDF
