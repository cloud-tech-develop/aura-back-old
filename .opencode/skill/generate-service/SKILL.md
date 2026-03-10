---
name: generate-service
description: Generate Spring Boot infrastructure (Entity, Service, Repositories) following Aura POS modular architecture
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: spring-boot
  layer: backend-service
---

# 🛠 generate-service Skill (Spring Boot)

## What I do

I generate the complete backend infrastructure for a new entity in Spring Boot. This includes:

- **Entity**: JPA definition with Lombok.
- **Service Interface**: Business logic contract.
- **Service Implementation**: Logic execution with `@Transactional`.
- **JPA Repository**: Basic CRUD operations (Mutations).
- **Query Repository**: Optimized data retrieval with `NamedParameterJdbcTemplate` (Queries).

I follow a strict separation between **Mutations (JPA)** and **Queries (SQL)** to maintain performance and scalability.

---

## When to use me

Use this skill when:

- You need to create a new module or feature in the backend.
- You need a standard CRUD with optimized search capabilities.
- You want to maintain the Aura POS pattern of segregated repositories.

---

## Parameters

Required parameters:

- `entityName`: Name of the entity (e.g., `Producto`).
- `packageName`: Sub-package name (e.g., `productos`).
- `fields`: List of fields with types.
- `tableName`: Database table name.

---

## Workflow

1.  **Generate Entity**: `src/main/java/com/cloud_technological/aura_pos/entity/{EntityName}Entity.java`
2.  **Generate Service Interface**: `src/main/java/com/cloud_technological/aura_pos/services/{EntityName}Service.java`
3.  **Generate Service Implementation**: `src/main/java/com/cloud_technological/aura_pos/services/implementations/{EntityName}ServiceImpl.java`
4.  **Generate JPA Repository**: `src/main/java/com/cloud_technological/aura_pos/repositories/{packageName}/{EntityName}JPARepository.java`
5.  **Generate Query Repository**: `src/main/java/com/cloud_technological/aura_pos/repositories/{packageName}/{EntityName}QueryRepository.java`

---

## Templates

### 1️⃣ Entity
```java
@Entity
@Table(name = "{table_name}")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {EntityName}Entity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Custom fields here
}
```

### 2️⃣ Service Interface
```java
public interface {EntityName}Service {
    PageImpl<{EntityName}Dto> listar(PageableDto<Object> pageable, Integer empresaId);
    {EntityName}Dto crear(Create{EntityName}Dto dto, Integer empresaId);
}
```

### 3️⃣ JPA Repository (Mutations)
```java
public interface {EntityName}JPARepository extends JpaRepository<{EntityName}Entity, Long> {
    Optional<{EntityName}Entity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
```

### 4️⃣ Query Repository (Queries)
```java
@Repository
public class {EntityName}QueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<{EntityName}TableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        // SQL with NamedParameterJdbcTemplate
    }
}
```

---

## Strict Enforcement

- Always use `@Transactional` in implementations for write operations.
- Always use `NamedParameterJdbcTemplate` for complex queries.
- Package names must be lowercase.
- Entities must ends with `Entity`.
- Implementation must ends with `ServiceImpl`.
