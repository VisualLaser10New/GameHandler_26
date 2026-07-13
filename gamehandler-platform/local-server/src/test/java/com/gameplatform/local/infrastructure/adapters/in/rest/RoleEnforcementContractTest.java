package com.gameplatform.local.infrastructure.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural / reflection-based role-enforcement contract test for the
 * local-server REST surface (Verifica 2). Walks every controller class under
 * {@code local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/}
 * (excluding the {@code Internal*} controllers — those are guarded by the
 * {@code InternalApiKeyFilter} and intentionally carry no JWT
 * {@code @PreAuthorize}) and verifies that:
 *
 * <ul>
 *   <li>each {@code @GetMapping}/{@code @PostMapping}/{@code @PutMapping}/
 *       {@code @DeleteMapping}/{@code @RequestMapping>-with-method} method
 *       is covered by an effective {@link PreAuthorize} annotation either at
 *       the method or at the class level (intentional authenticated-catch-all
 *       exceptions are documented);</li>
 *   <li>the {@code @PreAuthorize} expression contains only recognized role
 *       literals (PLAYER, LOCAL_ADMIN, GAME_ADMIN, PLATFORM_ADMIN) and/or
 *       {@code isAuthenticated()} — drift towards legacy literals
 *       ({@code USER}/{@code ADMIN}) would fail the test (Verifica 3 cross-check).</li>
 * </ul>
 *
 * <p>The existing slice tests (e.g. {@code AdminLocalControllerTest} /
 * {@code DeviceRegistrationControllerTest} / {@code PlatformAdminTournamentControllerTest})
 * verify behaviour and building/self-checks; this test complements them by
 * giving the role contract regression teeth — a refactor that drops or
 * loosens the {@code @PreAuthorize} expression on any endpoint is now a
 * build-time failure.</p>
 */
class RoleEnforcementContractTest {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "PLAYER", "LOCAL_ADMIN", "GAME_ADMIN", "PLATFORM_ADMIN");

    /**
     * Controllers that intentionally carry no {@code @PreAuthorize} annotation;
     * their access control is implemented in {@code SecurityConfig}
     * ({@code /api/auth/** permitAll}, {@code GET /api/auth/me authenticated},
     * {@code .anyRequest().authenticated()} for the rest). Endpoints here are
     * either public, generic-authenticated-catch-all or rely on a per-request
     * self-check inside the controller body (e.g. {@code AdminRequestsController}
     * filters by the principal's userId).
     */
    private static final Set<String> ALLOWED_NO_PREAUTHORIZE_CLASSES = Set.of(
            "com.gameplatform.local.infrastructure.adapters.in.rest.AuthController",
            "com.gameplatform.local.infrastructure.adapters.in.rest.PlayerTournamentSummaryController",
            "com.gameplatform.local.infrastructure.adapters.in.rest.AdminRequestsController");

    @Test
    void everyPublicRestEndpointHasPreAuthorize() throws Exception {
        Path restRoot = projectRoot().resolve(
                "local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest");
        assertThat(Files.exists(restRoot)).isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(restRoot)) {
            List<Path> controllers = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Controller.java"))
                    .filter(p -> !p.getFileName().toString().startsWith("Internal"))
                    .collect(Collectors.toList());

            assertThat(controllers).isNotEmpty();

            for (Path controller : controllers) {
                String fqcn = fqcn(controller);
                Class<?> clazz = Class.forName(fqcn);

                PreAuthorize classLevel = clazz.getAnnotation(PreAuthorize.class);

                for (Method m : clazz.getDeclaredMethods()) {
                    if (!hasMappingAnnotation(m)) {
                        continue;
                    }
                    PreAuthorize methodLevel = m.getAnnotation(PreAuthorize.class);

                    String effective = methodLevel != null ? methodLevel.value()
                            : (classLevel != null ? classLevel.value() : null);

                    if (effective == null) {
                        if (!ALLOWED_NO_PREAUTHORIZE_CLASSES.contains(fqcn)) {
                            violations.add(fqcn + "#" + m.getName()
                                    + " — no @PreAuthorize at method or class level and class not in ALLOWED_NO_PREAUTHORIZE_CLASSES allowlist");
                        }
                        continue;
                    }

                    verifyExpressionRoles(fqcn + "#" + m.getName(), effective, violations);
                }
            }
        }

        assertThat(violations)
                .as("REST endpoints without (or with malformed) @PreAuthorize role enforcement")
                .isEmpty();
    }

    private void verifyExpressionRoles(String location, String expr, List<String> violations) {
        for (String token : Arrays.asList("PLAYER", "LOCAL_ADMIN", "GAME_ADMIN", "PLATFORM_ADMIN",
                "USER", "ADMIN", "ROLE_PLAYER", "ROLE_LOCAL_ADMIN",
                "ROLE_GAME_ADMIN", "ROLE_PLATFORM_ADMIN")) {
            if (expr.contains("'" + token + "'")) {
                if (!ALLOWED_ROLES.contains(token)) {
                    violations.add(location + " — @PreAuthorize references legacy/unknown role '"
                            + token + "': " + expr);
                }
            }
        }
    }

    private boolean hasMappingAnnotation(Method m) {
        return m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null
                || m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) != null
                || m.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class) != null
                || m.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null
                || m.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class) != null
                || m.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class) != null;
    }

    private String fqcn(Path controller) {
        Path restRoot = projectRoot().resolve(
                "local-server/src/main/java");
        Path rel = restRoot.relativize(controller);
        String s = rel.toString().replace('\\', '/').replace('/', '.');
        return s.substring(0, s.length() - ".java".length());
    }

    private Path projectRoot() {
        Path p = Paths.get(System.getProperty("user.dir"));
        while (p != null) {
            if (Files.isDirectory(p.resolve("central-system/src/main/java"))
                    && Files.isDirectory(p.resolve("local-server/src/main/java"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(System.getProperty("user.dir"));
    }
}