# Checklist — Ciclo de nómina en paralelo (F8)

**Objetivo:** validar que Aura liquida **igual** que el sistema actual del cliente
antes de cortar en frío. Se corre un mes completo con novedades reales, se compara
**peso por peso**, se genera PILA y se saca un CUNE. Recién ahí: producción.

> Regla de oro: si una fila da diferente por más de **redondeo (≤ $1)**, NO se pasa
> a producción hasta entender por qué.

---

## 0. Preparación (una vez)

- [ ] Correr las migraciones nuevas: **V129, V130, V131** (y confirmar que Flyway
      quedó en `V131` con `ddl-auto=validate` sin errores al arrancar).
- [ ] Empresa de prueba con su **config de nómina** real: SMMLV, auxilio de
      transporte, modo (COMPLETO), exoneración Ley 1607 si aplica, y el toggle de
      **vacaciones anticipadas** como lo quiera el cliente.
- [ ] Cargar 3–5 empleados representativos (uno con salario ~mínimo, uno un poco
      más alto, uno con salario integral si aplica).
- [ ] **Saldos iniciales** (pantalla nueva): fecha de ingreso real, días de
      vacaciones, cesantías acumuladas. Sin esto los anuales salen mal.
- [ ] Afiliaciones (EPS/AFP/ARL/CCF) por contrato, con la ARL en su nivel de riesgo.

---

## 1. Nómina del mes — casos a probar

Para cada caso: liquidar en Aura, liquidar en el sistema actual (o a mano) y llenar
la tabla. Compará **cada renglón**, no solo el neto.

| Concepto | Aura | Sistema actual | Dif. | ¿OK? |
|---|---|---|---|---|
| Salario proporcional | | | | |
| Auxilio de transporte | | | | |
| Horas extra / recargos | | | | |
| Salud empleado (4%) | | | | |
| Pensión empleado (4%) | | | | |
| Fondo solidaridad (si ≥4 SMMLV) | | | | |
| Retención en la fuente | | | | |
| **Neto a pagar** | | | | |
| Aportes empleador (salud/pensión/ARL/CCF/ICBF/SENA) | | | | |
| Provisiones (prima/cesantías/int./vac.) | | | | |

### Casos concretos (lo que estrena F0–F5)

1. **Empleado normal, mes completo.** Verifica salud/pensión = 4% del IBC (sin
   auxilio), y que el auxilio NO infla el IBC.
2. **Incapacidad enfermedad general (5 días).** Comprobar:
   - [ ] El **salario se paga por 25 días**, no 30 (F0).
   - [ ] El **auxilio también baja** a 25 días.
   - [ ] La incapacidad la calcula el sistema: **2/3 del IBC/día**, con **piso de
         1 SMMLV/día**; los **2 primeros días a cargo del empleador** y el resto
         EPS (ver la descripción de la novedad).
3. **Incapacidad riesgo laboral (ARL).** Debe salir al **100%** desde el día 1.
4. **Licencia NO remunerada (3 días).** El salario baja 3 días y **no se paga**
   nada por esos días. En PILA debe aparecer la novedad **SLN**.
5. **Licencia remunerada.** Se paga normal (no baja días) y en PILA sale como
   **VAC/LR** (pide fechas).
6. **Vacaciones sin saldo suficiente** (con el toggle de anticipadas **apagado**):
   el sistema debe **bloquear** con el mensaje de saldo. Con el toggle encendido,
   debe **permitir**. Recordar: cuenta **días hábiles** (sin domingos ni festivos).
7. **Retención en la fuente** (si hay un empleado que la cause): comparar contra el
   cálculo del contador. Para salarios bajos debe dar **cero** (correcto).

---

## 2. Prestaciones (F4)

- [ ] **Generar en lote** una prima del semestre para todos los activos y comparar
      el total contra el sistema actual.
- [ ] En un empleado con **cesantías de saldo inicial**, verificar que la primera
      liquidación de cesantías **incluye ese saldo** (queda en la observación) y que
      el saldo inicial queda en cero después.
- [ ] **Liquidación definitiva** de un retiro: cesantías + intereses + prima
      proporcional + vacaciones (+ indemnización si es despido sin justa causa).
- [ ] Pagar una prestación y confirmar que **genera el asiento contable**.

---

## 3. PILA

- [ ] Generar el archivo PILA del período y correr el **validador** (bandeja de
      hallazgos): no debe quedar ningún cotizante **BLOQUEADO**.
- [ ] Verificar que las novedades del mes salen en el archivo (ING/RET, IGE/IRL,
      LMA, VAC/LR, SLN, VSP, traslados).
- [ ] Subir el archivo al **operador real** y confirmar que lo acepta.

---

## 4. Nómina electrónica (F6 — se prueba aparte)

- [ ] Con la empresa habilitada en Factus para el documento 26, enviar una nómina
      y obtener un **CUNE aceptado**.
- [ ] Repetir con una nómina que tenga **incapacidad** (queda pendiente hasta F6:
      hoy el builder solo cubre el caso mensual regular).

---

## 5. Criterios de aceptación (para dar el "sí" a producción)

- [ ] Todas las filas de la tabla del punto 1 cuadran (≤ $1 de diferencia).
- [ ] El archivo PILA lo **acepta el operador**.
- [ ] Sale **al menos un CUNE** de nómina electrónica.
- [ ] El contador del cliente **revisa y aprueba** una liquidación completa.
- [ ] Los asientos contables de nómina y prestaciones cuadran con el mayor.

**Solo cuando todo lo anterior está marcado se cambia del sistema viejo a Aura.**
Mientras tanto, se corre en paralelo.
