# Analysis Report: `game_catalog` table and `status` property discrepancy

**Project:** GameHandler 2.6  
**Databases analyzed:** Central System MySQL, Local Server MySQL  
**Code modules analyzed:** `central-system`, `local-server`, `shared/shared-domain`  
**Date:** 2026-07-01  
**Constraint:** No code edits were performed.

---

## 1. Executive Summary

This report analyzes the `game_catalog` table as defined in the Central System database, the Local Server database, and the Java code that uses it. The primary finding is a schema inconsistency in the `status` column:

- **Central System database:** `status` is defined as `ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE')`.
- **Local Server database:** `status` is defined as `VARCHAR(20) DEFAULT 'AVAILABLE'`.
- **Local Server Java code:** the domain model uses the shared enum `GameMachineStatus`, but the JPA entity `GameJpaEntity` stores `status` as a plain `String` and relies on manual conversion in `GameMapper`.
- **Central System Java code:** no entity, repository, or service references `game_catalog`. The table exists only at the DDL level in `infrastructure/mysql-central/init.sql`.

The discrepancy means that the Local Server gives up the database-level value constraints that the Central System enforces. This is a latent defect: the application behaves correctly today because `GameMapper` only writes enum names, but any direct SQL insert, migration script, or future code change can introduce invalid status values into the local database without failing at the DB layer.

---

## 2. Scope and Methodology

### 2.1 Files examined

| File | Purpose |
|------|---------|
| `gamehandler-platform/infrastructure/mysql-central/init.sql` | DDL for the Central System MySQL database. |
| `gamehandler-platform/infrastructure/mysql-local/init.sql` | DDL for the Local Server MySQL database. |
| `shared/shared-domain/src/main/java/com/gameplatform/shared/domain/model/GameMachineStatus.java` | Shared enum with the four allowed status values. |
| `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/entity/GameJpaEntity.java` | JPA mapping for `game_catalog` in the Local Server. |
| `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/mapper/GameMapper.java` | Manual conversion between enum and `String`. |
| `local-server/src/main/java/com/gameplatform/local/domain/model/Game.java` | Domain model enforcing state transitions. |
| `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/GameRepositoryAdapter.java` | Repository adapter delegating to JPA. |
| `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/repository/GameJpaRepository.java` | Spring Data JPA query methods. |
| `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/GameController.java` | REST endpoint exposing game state. |

### 2.2 Methodology

1. Compared the two DDL scripts line-by-line.
2. Traced all Java references to `game_catalog` and `GameMachineStatus` in the `local-server` and `central-system` modules.
3. Inspected the mapping layer to understand how the domain enum is persisted.
4. Verified that no Central System Java code loads or writes `game_catalog` rows.

---

## 3. Database Schema Comparison

### 3.1 Central System — `game_catalog`

```sql
CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);
```

The Central System schema uses a MySQL `ENUM` with the exact four values defined by the shared domain model.

### 3.2 Local Server — `game_catalog`

```sql
CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      VARCHAR(20) DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);
```

The Local Server schema is identical except for the `status` column, which is a plain `VARCHAR(20)`.

### 3.3 Side-by-side diff

| Central System | Local Server |
|----------------|--------------|
| `status ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE'` | `status VARCHAR(20) DEFAULT 'AVAILABLE'` |

---

## 4. Code Analysis

### 4.1 Shared domain enum

The canonical status values are defined in the shared domain module:

```java
package com.gameplatform.shared.domain.model;

public enum GameMachineStatus {
    AVAILABLE,
    RESERVED,
    IN_USE,
    MAINTENANCE
}
```

This enum is consumed by the Local Server domain model, DTOs, MQTT payloads, and tests. It is the single source of truth for valid status values.

### 4.2 Local Server JPA entity

`GameJpaEntity` maps `game_catalog` as a `String` for `status`:

```java
@Entity
@Table(name = "game_catalog")
public class GameJpaEntity {

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ... getters/setters
}
```

Because the column is `nullable = false` in JPA but `DEFAULT 'AVAILABLE'` in the database, there is also a subtle mismatch: JPA will fail validation before reaching the DB if `status` is `null`, whereas the DB would silently insert `'AVAILABLE'`.

### 4.3 Mapping layer

`GameMapper` performs manual enum/`String` conversion using `Enum.name()` and `Enum.valueOf()`:

```java
@Component
public class GameMapper {

    public Game toDomain(GameJpaEntity entity) {
        return new Game(
            new GameId(entity.getId()),
            GameType.valueOf(entity.getGameType()),
            entity.getName(),
            new BuildingId(entity.getBuildingId()),
            GameMachineStatus.valueOf(entity.getStatus())
        );
    }

    public GameJpaEntity toEntity(Game domain) {
        return new GameJpaEntity(
            domain.getId().id(),
            domain.getGameType().name(),
            domain.getName(),
            domain.getBuildingId().id(),
            domain.getStatus().name()
        );
    }
}
```

This mapping is correct for well-formed data, but it is brittle. If the local database contains an invalid status value, `GameMapper.valueOf()` will throw `IllegalArgumentException` at runtime when the row is loaded.

### 4.4 Repository and controller usage

`GameRepositoryAdapter.findByStatus(GameMachineStatus status)` converts the enum to a `String` before querying:

```java
@Override
public List<Game> findByStatus(GameMachineStatus status) {
    return jpaRepository.findByStatus(status.name()).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
}
```

`GameController` exposes the status through `GameStateDto`, which carries the `GameMachineStatus` enum directly.

### 4.5 Central System code

A full search of the `central-system` module found **zero references** to `game_catalog`, `GameCatalog`, or `GameMachineStatus`. The Central System does not appear to manage or query the game catalog table at runtime. The table is created solely by `infrastructure/mysql-central/init.sql`.

This raises a design question: if the Central System is intended to hold a global catalog, the corresponding JPA entity, repository, and service are missing. If the Central System is not intended to use the table, the DDL may be dead schema.

---

## 5. Why One Is `ENUM` and One Is `VARCHAR`

There is no code comment, design note, or version-control evidence in the examined files explaining the intentional choice of `VARCHAR` in the Local Server. Based on the artifacts observed, the most plausible explanations are:

1. **Unintended schema drift:** the local DDL was written or generated separately from the central DDL, and the `ENUM` was simply omitted. The shared Java enum and the Central System DDL suggest that `ENUM` was the intended type.
2. **JPA portability preference:** some teams avoid MySQL `ENUM` because it is vendor-specific and harder to map cleanly with JPA. However, the project already uses MySQL-specific DDL in both databases (e.g., `JSON` columns, `DATETIME(6)`, `UNIQUE KEY`), so this argument is weak.
3. **Code-first generation:** the local schema may have been derived from the JPA entity, which declares `status` as `String`. Because JPA does not automatically create an `ENUM` DDL for a `String` field, Hibernate would emit `VARCHAR(20)`.

The third explanation is the most consistent with the evidence: the JPA entity drove the DDL, and the entity uses `String` because the original author did not annotate the field with `@Enumerated(EnumType.STRING)`. The Central System DDL, written by hand, correctly used `ENUM`.

---

## 6. Risks and Impact

- **Data integrity:** the Local Server database can store arbitrary strings such as `'BROKEN'`, `'OFFLINE'`, or typos like `'AVAILBLE'`. The Central System would reject the same values.
- **Runtime failures:** loading a row with an invalid status will throw `IllegalArgumentException` in `GameMapper.toDomain()`, potentially crashing requests or background jobs.
- **Silent inconsistency in replicas:** if the Central System ever synchronizes `game_catalog` rows to Local Servers, valid central rows will insert correctly, but local rows with invalid statuses cannot be replicated to the central `ENUM` column.
- **Maintainability:** developers must remember that status validation is enforced only in Java, not in the local DB. Future scripts, migrations, or direct SQL can bypass the rules.
- **Test coverage:** existing tests (e.g., `GameMapperTest`, `GameTest`) exercise only valid enum values. No test verifies behavior with malformed database strings.

---

## 7. Recommendations

1. **Align the Local Server DDL with the Central System DDL:** change `status` to `ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE'`.
2. **Improve the JPA mapping:** annotate the `status` field in `GameJpaEntity` with `@Enumerated(EnumType.STRING)` and use the `GameMachineStatus` enum type. This makes the entity self-documenting and prevents future DDL generation from reverting to `VARCHAR`.
3. **Reconcile the nullable mismatch:** decide whether `status` is mandatory in the domain (current JPA says `nullable = false`) or optional with a DB default. Update either the JPA annotation or the DDL accordingly.
4. **Clarify the Central System role:** either add the missing Central System JPA entity/repository for `game_catalog` or remove the table from the central DDL if it is not used. Leaving orphaned DDL is a maintenance burden.
5. **Add defensive tests:** introduce mapper and repository tests that verify rejection or graceful handling of invalid status strings until the schema is aligned.

---

## 8. Conclusion

The `status` property discrepancy between the Central System (`ENUM`) and the Local Server (`VARCHAR`) is a schema-level inconsistency. The application currently compensates through manual enum/`String` conversion in `GameMapper`, but the Local Server database cannot enforce the same value constraints as the Central System. The most likely root cause is that the Local Server DDL was generated from or influenced by the JPA entity, which declares `status` as `String` rather than as the shared `GameMachineStatus` enum. Aligning the schema and the JPA mapping would remove this latent risk and make the two databases consistent with the shared domain model.

---

## 9. Appendix: Evidence Quotations

### 9.1 Central System DDL

```sql
CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);
```

### 9.2 Local Server DDL

```sql
CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      VARCHAR(20) DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);
```

### 9.3 Shared enum

```java
public enum GameMachineStatus {
    AVAILABLE,
    RESERVED,
    IN_USE,
    MAINTENANCE
}
```

### 9.4 JPA entity field

```java
@Column(name = "status", nullable = false, length = 20)
private String status;
```

### 9.5 GameMapper conversion

```java
GameMachineStatus.valueOf(entity.getStatus())
// and
domain.getStatus().name()
```

### 9.6 Central System absence

Search pattern: `game_catalog|GameCatalog|GameMachineStatus`  
Module: `gamehandler-platform/central-system`  
Result: **0 matches** — no Central System Java source references the table or its status enum.
