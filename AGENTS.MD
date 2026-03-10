# AGENTS.md - AURA POS

This file provides guidelines for agents working on the AURA POS project (frontend + backend).

---

## Project Overview

### Frontend (aura-frontend)
- **Framework**: Angular 18 (Standalone Components)
- **UI Library**: PrimeNG 18 + PrimeFlex + PrimeIcons
- **State**: RxJS + Services
- **Storage**: localforage (IndexedDB for session)
- **Build**: Angular CLI

### Backend (aura-pos)
- **Framework**: Spring Boot 3.5.10
- **Language**: Java 17
- **Database**: PostgreSQL
- **Build Tool**: Maven
- **Security**: Spring Security 6 with JWT
- **API Documentation**: Swagger (springfox)

## Documentation

Project documentation is located in the `docs/` directory:
- **Epics**: `docs/epics/` - High-level feature descriptions
- **User Stories**: `docs/hu/` - Detailed user stories with acceptance criteria

### Existing Epics

| Epic ID | Title | Description |
|---------|-------|-------------|
| EP-001 | Módulo de Empresa y Sucursales | Gestión de empresas y sucursales |
| EP-002 | Módulo de Terceros | Gestión de clientes, proveedores y empleados |
| EP-003 | Módulo de Ventas y Compras | Gestión de ventas y compras |
| EP-004 | Módulo de Cuentas por Cobrar y Pagar | Gestión de cuentas, abonos y saldos |

### User Stories

| HU ID | Epic | Title |
|-------|------|-------|
| HU-001 a HU-008 | EP-001 a EP-003 | Módulos anteriores |
| HU-009 | EP-004 | Gestión de Cuentas por Cobrar |
| HU-010 | EP-004 | Gestión de Cuentas por Pagar |
| HU-011 | EP-004 | Abonos a Cuentas por Cobrar |
| HU-012 | EP-004 | Abonos a Cuentas por Pagar |
| HU-013 | EP-004 | Cálculo de Totales de Cuentas |

## Build Commands

### Frontend (Angular)

```bash
# Development server
npm start
# or: ng serve

# Build for production
npm run build

# Run tests
npm test

# Run single test (Karma)
# Edit karma.conf.js to add single-run: true, then:
npm test -- --no-watch --browsers=ChromeHeadless

# Watch mode for testing
npm test -- --watch
```

### Backend (Spring Boot)

```bash
# Build project
mvn clean install

# Run application
mvn spring-boot:run

# Run tests
mvn test

# Run single test class
mvn test -Dtest=TestClassName

# Run single test method
mvn test -Dtest=TestClassName#testMethodName

# Package (skip tests)
mvn package -DskipTests
```

## Project Structure

```
src/main/java/com/cloud_technological/aura_pos/
├── config/           # Configuration classes
├── controllers/      # REST controllers (@RestController)
├── dto/              # Data Transfer Objects
│   └── [module]/     # DTOs organized by module
├── entity/           # JPA entities
├── mappers/          # MapStruct mappers
├── repositories/     # JPA repositories + Query repositories
│   ├── [module]/     # JPA repositories
│   └── [module]/
│       └── QueryRepository.java  # Custom JDBC queries
├── security/         # JWT, SecurityConfig, SecurityUtils
├── services/         # Service interfaces + implementations
│   └── implementations/
├── utils/            # Utilities, DTOs (PageableDto, ApiResponse)
```

---

## Frontend Guidelines (Angular 18 + PrimeNG)

### Project Structure

```
src/app/
├── core/
│   ├── guards/         # Auth guards
│   ├── interceptors/   # HTTP interceptors
│   ├── models/        # Global models
│   └── services/      # Global services
├── shared/
│   ├── pipes/         # AlertService, HelperService
│   └── utils/        # ColsModel, FilterTable, ResponseModel
├── layout/            # MainLayout, Sidebar, Topbar
└── features/
    ├── auth/          # Login
    ├── dashboard/
    ├── pos/          # Punto de venta (special layout)
    ├── catalogo/      # Categorías, Marcas, Productos
    ├── mermas/
    ├── traslados/
    ├── cuentas/       # Cuentas por Cobrar/Pagar
    └── reportes/
```

### Feature Module Structure

Each feature follows this pattern:
```
features/mermas/
├── index/             # Table component
│   ├── index-mermas.component.ts
│   ├── index-mermas.component.html
│   └── index-mermas.component.scss
├── form/              # Create/Edit dialog
├── detalle/           # View detail dialog
├── motives/           # Sub-module (if needed)
├── models/            # All models in single file
├── services/          # API services
└── [feature].routes.ts
```

### Angular Conventions

| Type | Convention | Example |
|------|------------|---------|
| Components | kebab-case | `index-cuentas-cobrar` |
| Services | kebab-case + .service | `cuenta-cobrar.service.ts` |
| Models | kebab-case + .model | `cuenta-cobrar.model.ts` |
| Routes | kebab-case | `cuentas-cobrar` |

### Component Pattern

```typescript
@Component({
  selector: 'app-index-cuentas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,  // ALWAYS
  imports: [...],
  providers: [MessageService, ConfirmationService],  // ONLY in index
  templateUrl: './index-cuentas.component.html',
  styleUrls: ['./index-cuentas.component.scss'],    // SCSS per component
})
export class IndexCuentasComponent implements OnInit {
  constructor(
    private readonly cdr: ChangeDetectorRef,  // ALWAYS for async
  ) {}

  async load(): Promise<void> {
    this.loading = true;
    try {
      const res = await lastValueFrom(this.service.page({ ... }));
      this.rows = res?.data?.content ?? [];
    } catch { /* handle error */ }
    finally {
      this.loading = false;
      this.cdr.markForCheck();  // ALWAYS in finally
    }
  }
}
```

### Form Pattern (ReactiveForms)

```typescript
@Component({
  selector: 'app-form-cuenta',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ...],
  templateUrl: './form-cuenta.component.html',
})
export class FormCuentaComponent implements OnChanges {
  @Input() visible = false;
  @Input() cuenta: any = null;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<void>();

  form: FormGroup;

  get isEdit(): boolean { return !!this.cuenta; }

  ngOnChanges(): void {
    if (this.visible) {
      this.form.reset({ ... });
      if (this.cuenta) this.form.patchValue(this.cuenta);
    }
  }

  async save(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    // ... save logic
  }
}
```

### Key Rules

1. **Change Detection**: ALWAYS use `ChangeDetectionStrategy.OnPush` + `cdr.markForCheck()`
2. **Async**: Use `lastValueFrom` from `rxjs` for async/await
3. **Forms**: Use ReactiveFormsModule + formControlName (NOT ngModel except for filters)
4. **Dialogs**: Forms are ALWAYS dialogs, never separate routes
5. **SCSS**: Each component has its own SCSS file (NEVER shared)
6. **Loading**: Show loading state in try/catch/finally blocks

### Currency Formatting

```typescript
formatCOP = (v: number): string =>
  new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(v ?? 0);
```

### Import Order

```typescript
// Angular
import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

// PrimeNG
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';

// RxJS
import { lastValueFrom } from 'rxjs';

// Project
import { AlertService } from '../../../shared/pipes/alert.service';
import { SomeService } from '../services/some.service';
```

### API Service Pattern

```typescript
@Injectable({ providedIn: 'root' })
export class CuentaCobrarService {
  private readonly base = `${environment.apiUrl}cuentas-cobrar`;

  constructor(private readonly http: HttpClient) {}

  page(pageable: any): Observable<any> {
    return this.http.post<any>(`${this.base}/page`, pageable);
  }

  getById(id: number): Observable<any> {
    return this.http.get<any>(`${this.base}/${id}`);
  }

  create(dto: any): Observable<any> {
    return this.http.post<any>(`${this.base}`, dto);
  }
}
```

---

## Code Style Guidelines

### Architecture Pattern

Follow **CQRS Light**:
1. **Controller**: Receive HTTP requests, validate DTOs, return ApiResponse. NO business logic.
2. **Service (Interface + Impl)**: Contains business logic, manages transactions (@Transactional).
3. **Mapper**: MapStruct for DTO <-> Entity conversions.
4. **Repository**: JPA for simple CRUD; QueryRepository (JDBC) for complex queries.

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Entities | PascalCase, singular | `Producto`, `Venta` |
| Controllers | `XxxController` | `ProductoController` |
| Services | `XxxService` + `XxxServiceImpl` | `ProductoService`, `ProductoServiceImpl` |
| Repositories | `XxxRepository` | `ProductoRepository` |
| Query Repositories | `XxxQueryRepository` | `ProductoQueryRepository` |
| DTOs | `XxxDto`, `CreateXxxDto`, `UpdateXxxDto`, `XxxTableDto` | `ProductoDto`, `CreateProductoDto` |
| Mappers | `XxxMapper` | `ProductoMapper` |
| Tables | snake_case | `producto`, `venta_detalle` |
| Columns | snake_case | `empresa_id`, `fecha_creacion` |

### Package by Module

DTOs, entities, repositories, services, and controllers should be organized by module:
```
dto/productos/
  ├── CreateProductoDto.java
  ├── ProductoDto.java
  ├── ProductoTableDto.java
  └── UpdateProductoDto.java
```

### DTO Structure

- **XxxDto**: Full detail (form/get by ID)
- **XxxTableDto**: Paginated table (includes `totalRows`)
- **CreateXxxDto**: Creation request (with validations)
- **UpdateXxxDto**: Update request (all fields optional)

### Security Rules

- **ALWAYS** extract `empresaId` from JWT token via `SecurityUtils.getEmpresaId()`
- **NEVER** accept `empresaId` from request body or header
- Use `SecurityUtils.getSucursalId()`, `getUsuarioId()`, `getRol()` as needed

### Pagination

- All list endpoints use `POST /page` with `PageableDto`
- Query must use `COUNT(*) OVER() AS total_rows`
- Return `PageImpl` as response

### Database Patterns

**Soft Delete**:
- Entities with `deleted_at`: Use `@Where(clause = "deleted_at IS NULL")`
- Entities with `activo` boolean flag: Filter in queries

**Inventory Rules** (CRITICAL):
1. Stock is NEVER modified directly
2. All stock changes go through `movimiento_inventario` (immutable)
3. Every movement records `saldo_anterior` and `saldo_nuevo`
4. Anulations must validate stock availability (never negative)

**QueryRepository Pattern**:
- Use for complex queries, reports, paginated lists
- Use `NamedParameterJdbcTemplate`
- Return DTOs directly with `BeanPropertyRowMapper`
- Methods: `existeNombre()`, `existeNombreExcluyendo()`

### Import Order

```java
// 1. Java/Jakarta
import java.util.*;
import javax.validation.*;

// 2. Spring
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

// 3. Project imports (grouped by package)
import com.cloud_technological.aura_pos.dto.*;
import com.cloud_technological.aura_pos.services.*;
import com.cloud_technological.aura_pos.repositories.*;
```

### Error Handling

- Use `GlobalException` for custom errors
- All controller responses wrap in `ApiResponse<T>`
- HTTP status codes: 200 (OK), 201 (Created), 400 (Bad Request), 401 (Unauthorized), 404 (Not Found), 500 (Error)

### Lombok Usage

Use `@Data`, `@Entity`, `@Table`, `@Service`, `@Repository`, `@Autowired` appropriately. Avoid manual getters/setters.

### MapStruct

- One mapper per module
- Mark relationships as ignored in mapper, assign manually in Service

### Testing

- Tests go in `src/test/java/com/cloud_technological/aura_pos/`
- Use JUnit 5 (`org.junit.jupiter.api.*`)
- Use `@SpringBootTest` for integration tests
- Single test execution: `mvn test -Dtest=ClassName#methodName`

### Common Patterns

**Creating a new module** (in order):
1. SQL migration (if new table)
2. Entity
3. DTOs
4. JPARepository (simple queries)
5. QueryRepository (complex queries, paginated)
6. Mapper (MapStruct)
7. Service Interface + Implementation
8. Controller

**Multi-company filtering**:
```java
Integer empresaId = securityUtils.getEmpresaId();
// Always filter queries by empresaId
```

## API Response Format

```json
{
  "status": 200,
  "message": "Description",
  "error": false,
  "data": { ... }
}
```

## Key Files

- Security: `src/main/java/com/cloud_technological/aura_pos/security/`
- Exception handling: `src/main/java/com/cloud_technological/aura_pos/utils/GlobalException.java`
- API Response: `src/main/java/com/cloud_technological/aura_pos/utils/ApiResponse.java`
- SecurityUtils: `src/main/java/com/cloud_technological/aura_pos/utils/SecurityUtils.java`

## API Endpoints

### Cuentas por Cobrar (EP-004 - HU-009, HU-011, HU-013)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/cuentas-cobrar/page` | Listar cuentas con paginación y filtros |
| GET | `/api/cuentas-cobrar/{id}` | Obtener detalle de cuenta |
| POST | `/api/cuentas-cobrar` | Crear cuenta por cobrar |
| PUT | `/api/cuentas-cobrar/{id}` | Actualizar cuenta |
| POST | `/api/cuentas-cobrar/{id}/abonos` | Registrar abono (entrada de caja) |
| GET | `/api/cuentas-cobrar/{id}/abonos` | Listar abonos de cuenta |
| DELETE | `/api/cuentas-cobrar/{cuentaId}/abonos/{abonoId}` | Eliminar abono |
| GET | `/api/cuentas-cobrar/resumen` | Obtener resumen (totales, activas, pagadas, vencidas) |
| GET | `/api/cuentas-cobrar/vencidas` | Listar cuentas vencidas |

**Filtros para `/page` y `/resumen`:**
- `fechaDesde` (String) - Fecha de emisión inicial
- `fechaHasta` (String) - Fecha de emisión final
- `clienteId` (Long) - Filtrar por cliente
- `estado` (String) - Estado: activa, pagada, vencida

### Cuentas por Pagar (EP-004 - HU-010, HU-012, HU-013)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/cuentas-pagar/page` | Listar cuentas con paginación y filtros |
| GET | `/api/cuentas-pagar/{id}` | Obtener detalle de cuenta |
| POST | `/api/cuentas-pagar` | Crear cuenta por pagar |
| PUT | `/api/cuentas-pagar/{id}` | Actualizar cuenta |
| POST | `/api/cuentas-pagar/{id}/abonos` | Registrar abono (salida de caja) |
| GET | `/api/cuentas-pagar/{id}/abonos` | Listar abonos de cuenta |
| DELETE | `/api/cuentas-pagar/{cuentaId}/abonos/{abonoId}` | Eliminar abono |
| GET | `/api/cuentas-pagar/resumen` | Obtener resumen (totales, activas, pagadas, vencidas) |
| GET | `/api/cuentas-pagar/vencidas` | Listar cuentas vencidas |

**Filtros para `/page` y `/resumen`:**
- `fechaDesde` (String) - Fecha de emisión inicial
- `fechaHasta` (String) - Fecha de emisión final
- `proveedorId` (Long) - Filtrar por proveedor
- `estado` (String) - Estado: activa, pagada, vencida

### Estructura de DTOs

**CuentaCobrarDto / CuentaPagarDto:**
```json
{
  "id": 1,
  "empresaId": 1,
  "terceroId": 1,
  "numeroCuenta": "CC-20260225-0001",
  "fechaEmision": "2026-02-25T10:00:00",
  "fechaVencimiento": "2026-03-25T10:00:00",
  "totalDeuda": 100000.00,
  "totalAbonado": 25000.00,
  "saldoPendiente": 75000.00,
  "estado": "activa",
  "observaciones": "Compra de mercancía",
  "createdAt": "2026-02-25T10:00:00"
}
```

**CuentaCobrarTableDto / CuentaPagarTableDto:**
```json
{
  "id": 1,
  "numeroCuenta": "CC-20260225-0001",
  "clienteNombre": "Juan Pérez",
  "clienteDocumento": "12345678",
  "fechaEmision": "2026-02-25T10:00:00",
  "fechaVencimiento": "2026-03-25T10:00:00",
  "totalDeuda": 100000.00,
  "totalAbonado": 25000.00,
  "saldoPendiente": 75000.00,
  "estado": "activa",
  "totalRows": 10
}
```

**ResumenDto:**
```json
{
  "totalCuentas": 50,
  "totalDeuda": 5000000.00,
  "totalAbonado": 1500000.00,
  "saldoPendiente": 3500000.00,
  "cantidadActivas": 30,
  "cantidadPagadas": 15,
  "cantidadVencidas": 5
}
```

**AbonoDto:**
```json
{
  "id": 1,
  "cuentaCobrarId": 1,
  "monto": 25000.00,
  "metodoPago": "efectivo",
  "referencia": "REC-001",
  "fechaPago": "2026-02-25T15:30:00",
  "usuarioNombre": "admin"
}
```
