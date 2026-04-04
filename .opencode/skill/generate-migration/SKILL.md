---
name: generate-migration
description: Generate Laravel database migrations following Aura POS migration conventions
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: laravel
  layer: database
---

# 🛠 generate-migration Skill (Laravel)

## What I do

I generate Laravel database migrations for the Aura POS migration project. This includes:

- **New table migrations**: Create new database tables.
- **Alter table migrations**: Add, modify, or remove columns.
- **Seed data migrations**: Insert initial data when needed.

I follow the existing migration naming convention: `YYYY_MM_DD_000XXX_description_table.php`

---

## When to use me

Use this skill when:

- You need to create a new table in the database.
- You need to modify an existing table (add columns, indexes, FKs).
- You need to add seed data for a table.
- The Spring Boot backend requires a column that doesn't exist yet.

---

## Parameters

Required parameters:

- `migrationType`: Type of migration (`create`, `alter`, `seed`)
- `tableName`: Database table name
- `columns`: List of columns with types (for create/alter)
- `description`: Brief description for the migration filename

Optional parameters:

- `foreignKeys`: List of foreign key definitions
- `indexes`: List of indexes to create
- `seedData`: Array of seed data to insert

---

## Migration Location

All migrations are created in:

```
C:\Users\Drako\Desktop\cloud-tecno\aura-pos-migracion-old\database\migrations\
```

---

## Workflow

1.  **Analyze the request**: Determine if it's a create, alter, or seed migration.
2.  **Generate migration file**: Create PHP file with proper naming convention.
3.  **Provide execution command**: Tell user how to run the migration.

---

## Templates

### 1️⃣ Create Table Migration

```php
<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('{table_name}', function (Blueprint $table) {
            $table->id();
            $table->foreignId('empresa_id')->constrained('empresa');
            $table->string('nombre', 150);
            $table->boolean('activo')->default(true);
            $table->timestamp('created_at')->nullable();
            $table->timestamp('updated_at')->nullable();

            $table->index(['empresa_id', 'activo'], 'idx_{table_name}_empresa');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('{table_name}');
    }
};
```

### 2️⃣ Alter Table Migration (Add Columns)

```php
<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('{table_name}', function (Blueprint $table) {
            $table->string('ciudad', 100)->nullable()->after('direccion');
            $table->integer('ciudad_id')->nullable()->after('ciudad');
        });
    }

    public function down(): void
    {
        Schema::table('{table_name}', function (Blueprint $table) {
            $table->dropColumn(['ciudad', 'ciudad_id']);
        });
    }
};
```

### 3️⃣ Seed Data Migration

```php
<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        $empresaExists = DB::table('empresa')->where('id', 1)->exists();
        if ($empresaExists) {
            DB::table('{table_name}')->insert([
                ['empresa_id' => 1, 'nombre' => 'VENDEDOR', 'activo' => true],
            ]);
        }
    }

    public function down(): void
    {
        DB::table('{table_name}')->where('empresa_id', 1)->delete();
    }
};
```

---

## Execution Command

After creating a migration, execute it with:

```bash
"C:\laragon\bin\php\php-8.3.30-Win32-vs16-x64\php.exe" artisan migrate
```

To run a specific migration:

```bash
php artisan migrate --path=/database/migrations/filename.php
```

---

## Strict Enforcement

- Follow the naming convention: `YYYY_MM_DD_000XXX_description_table.php`
- Always include `up()` and `down()` methods.
- For alter migrations, use `after()` to position columns correctly.
- Always add indexes for foreign keys and frequently queried columns.
- Use nullable() appropriately for optional fields.
- Use appropriate string lengths (50, 100, 150, 255, 500).
- Use `boolean` for flags, `integer` for IDs, `double` for coordinates.
- Always include `empresa_id` for multi-tenant tables.
