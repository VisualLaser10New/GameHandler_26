package com.gameplatform.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the seed-vs-compose Game ID alignment bug.
 *
 * <p>Asserts that every {@code GAME_ID=...} declared in {@code docker-compose.yml}
 * and {@code docker-compose.multi.yml} for an emulator client (each of which
 * carries a {@code BUILDING_ID=...} env) has a matching row in the
 * {@code game_catalog} seed of the {@code init*.sql} file of the corresponding
 * building. The catalog is seeded once at container bootstrap, and the local
 * server's {@code DeviceRegistrationController} validates device enrollment
 * with a {@code findById} on {@code game_catalog.id}; a mismatch causes the
 * client to be rejected with HTTP 403 ("Device is not pre-authorized in the
 * catalog").</p>
 *
 * <p>This is a pure file-parsing test: it boots no Spring context and needs no
 * database. It reads the files directly from the module-relative paths under
 * {@code gamehandler-platform/}, resolved from the surefire working directory
 * (the e2e-tests module) via {@code ..}. If the layout changes, update
 * {@link #PLATFORM_ROOT}.</p>
 */
@DisplayName("Seed (init*.sql) vs compose GAME_ID alignment")
class GameIdAlignmentTest {

    private static final Path PLATFORM_ROOT = Paths.get("..");
    private static final Path COMPOSE_MAIN = PLATFORM_ROOT.resolve("docker-compose.yml");
    private static final Path COMPOSE_MULTI = PLATFORM_ROOT.resolve("docker-compose.multi.yml");
    private static final Path INIT_DIR = PLATFORM_ROOT.resolve("infrastructure/mysql-local");

    private static final Pattern GAME_ID_PATTERN = Pattern.compile(
            "^[ \\t]*-[ \\t]*GAME_ID=([^#\\n]+?)\\s*(?:#.*)?$", Pattern.MULTILINE);
    private static final Pattern BUILDING_ID_PATTERN = Pattern.compile(
            "^[ \\t]*-[ \\t]*BUILDING_ID=([^#\\n]+?)\\s*(?:#.*)?$", Pattern.MULTILINE);
    private static final Pattern CATALOG_INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+game_catalog\\s*\\([^)]*\\)\\s*VALUES\\s*(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CATALOG_ROW_PATTERN = Pattern.compile(
            "\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");

    @Test
    @DisplayName("every emulator GAME_ID in docker-compose files is present in the matching building's init*.sql catalog")
    void everyEmulatorGameIdIsSeededInTheMatchingBuildingCatalog() throws IOException {
        Map<String, List<String>> gameIdsByBuilding = collectEmulatorGameIdsByBuilding();
        assertThat(gameIdsByBuilding)
                .as("at least one emulator GAME_ID declared in compose files")
                .isNotEmpty();

        Map<String, List<String>> catalogIdsByBuilding = collectCatalogIdsByBuilding();
        assertThat(catalogIdsByBuilding)
                .as("at least one game_catalog seed present")
                .isNotEmpty();

        List<String> failures = new ArrayList<>();
        gameIdsByBuilding.forEach((buildingId, gameIds) -> {
            List<String> seeded = catalogIdsByBuilding.get(buildingId);
            if (seeded == null || seeded.isEmpty()) {
                gameIds.forEach(g -> failures.add(
                        "GAME_ID '" + g + "' targets building '" + buildingId
                                + "' but no init*.sql seed for that building was found"));
                return;
            }
            for (String gameId : gameIds) {
                if (!seeded.contains(gameId)) {
                    failures.add("GAME_ID '" + gameId + "' (building '" + buildingId
                            + "') is not present in the game_catalog seed. Seeded IDs: " + seeded);
                }
            }
        });

        assertThat(failures)
                .as("all GAME_ID values declared by emulator clients in docker-compose*.yml "
                        + "must be pre-authorized in the game_catalog of the matching building's init*.sql "
                        + "(otherwise DeviceRegistrationController rejects enrollment with HTTP 403)")
                .isEmpty();
    }

    private Map<String, List<String>> collectEmulatorGameIdsByBuilding() throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        // The single-building compose file declares client-1 and client-2 with
        // BUILDING_ID=building-1; the multi override declares client-3 (building-2)
        // and client-4 (building-3). Both files use the same service-block layout:
        // each game-client-* service lists GAME_ID and BUILDING_ID as env entries.
        for (Path compose : new Path[]{COMPOSE_MAIN, COMPOSE_MULTI}) {
            if (!Files.exists(compose)) {
                continue;
            }
            String yaml = Files.readString(compose, StandardCharsets.UTF_8);
            // Split on top-level service headers ("  <name>:" at column 2) so each
            // block's env entries can be paired independently.
            String[] blocks = yaml.split("\\n(?=  [A-Za-z0-9_-]+:)");
            for (String block : blocks) {
                if (!block.contains("GAME_ID=")) {
                    continue;
                }
                List<String> gameIds = new ArrayList<>();
                Matcher gm = GAME_ID_PATTERN.matcher(block);
                while (gm.find()) {
                    gameIds.add(gm.group(1).trim());
                }
                List<String> buildingIds = new ArrayList<>();
                Matcher bm = BUILDING_ID_PATTERN.matcher(block);
                while (bm.find()) {
                    buildingIds.add(bm.group(1).trim());
                }
                if (gameIds.isEmpty() || buildingIds.isEmpty()) {
                    continue;
                }
                // A client service declares exactly one GAME_ID and one BUILDING_ID.
                // Pair them by index; if counts mismatch, fall back to the first building.
                for (int i = 0; i < gameIds.size(); i++) {
                    String building = i < buildingIds.size() ? buildingIds.get(i) : buildingIds.get(0);
                    result.computeIfAbsent(building, k -> new ArrayList<>()).add(gameIds.get(i));
                }
            }
        }
        return result;
    }

    private Map<String, List<String>> collectCatalogIdsByBuilding() throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (var stream = Files.list(INIT_DIR)) {
            for (Path init : (Iterable<Path>) stream::iterator) {
                String fileName = init.getFileName().toString();
                if (!fileName.endsWith(".sql")) {
                    continue;
                }
                String sql = Files.readString(init, StandardCharsets.UTF_8);
                Matcher im = CATALOG_INSERT_PATTERN.matcher(sql);
                while (im.find()) {
                    String values = im.group(1);
                    Matcher rm = CATALOG_ROW_PATTERN.matcher(values);
                    while (rm.find()) {
                        String id = rm.group(1);
                        String buildingId = rm.group(4);
                        result.computeIfAbsent(buildingId, k -> new ArrayList<>()).add(id);
                    }
                }
            }
        }
        return result;
    }
}