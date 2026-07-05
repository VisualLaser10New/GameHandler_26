package com.gameplatform.central;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural invariants (FASE 6 step 5, plan lines 214 & 263):
 *
 * <ol>
 *   <li><b>Empty stub adapter classes are forbidden.</b> No class under
 *       {@code .../infrastructure/adapters/out/} (of either module) may have
 *       an empty body — i.e. {@code class X implements Y { }} with no methods,
 *       no fields, no logic. Such a stub silently ships a port with no real
 *       implementation and breaks the hexagonal contract.</li>
 *   <li><b>No @Transactional method may loop over outbound port calls.</b>
 *       A method annotated {@code @Transactional} on a {@code @Service} or
 *       {@code @Component} in {@code application/service/} must NOT contain a
 *       {@code for}/{@code while}/{@code .forEach(} loop whose body calls an
 *       <em>external</em> outbound port ({@code com.gameplatform.*.domain.ports.out.*}
 *       types whose simple name ends with {@code Port}, e.g.
 *       {@code PushUserToLocalServersPort}, {@code PublishGameStatePort},
 *       {@code SyncCentralSystemPort}). This is exactly the class-of-bug fixed
 *       by FASE 6 poison-isolation refactor: a long tx that iterates external
 *       calls holds locks and a single failure poisons the whole batch
 *       (BUG-SYNC-01 / C-01). Persistence ports named {@code *Repository}
 *       are deliberately excluded — they are local DB calls atomic with the
 *       tx (e.g. {@code OutboxDlqPromotionService} intentionally sweeps the
 *       DLQ inside one tx).</li>
 * </ol>
 *
 * <p>Implemented with pure reflection/file-scan — no ArchUnit dependency (per
 * plan line 263). The tests MUST pass on the current codebase (no false
 * positives); if a test flags an existing file, that file is a real smell and
 * the assertion message names it so we can refactor.</p>
 */
class ArchitectureInvariantTest {

    /**
     * Invariant (a): no empty stub adapter classes in
     * {@code .../infrastructure/adapters/out/} of either module.
     *
     * <p>Heuristic (per plan line 214): after stripping comments and
     * whitespace, the file body matches {@code class \w+ ... \{ \}} — i.e. the
     * class declaration opens into an immediately-closed brace pair. This
     * catches the "class with no members at all" stub; it does NOT try to
     * detect classes with only no-op methods (that would require AST parsing
     * and is left as a known limitation).</p>
     */
    @Test
    void noEmptyStubAdapterClassesInOutAdaptersDirectories() throws IOException {
        List<Path> roots = outAdapterRoots();
        Pattern stubPattern = Pattern.compile(
                "\\bclass\\s+\\w+[^{]*\\{\\s*\\}");
        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> {
                            try {
                                String src = Files.readString(p);
                                String stripped = stripComments(src);
                                if (stubPattern.matcher(stripped).find()) {
                                    offenders.add(projectRoot().relativize(p).toString());
                                }
                            } catch (IOException ignored) {
                                // unreadable file: ignore; not a stub offence
                            }
                        });
            }
        }
        assertThat(offenders)
                .as("Empty stub adapter classes (only `class X implements Y { }` with no body) "
                        + "are forbidden in infrastructure/adapters/out/ of both modules")
                .isEmpty();
    }

    /**
     * Invariant (b): no {@code @Transactional} method on a {@code @Service} or
     * {@code @Component} class in {@code application/service/} may contain a
     * {@code for}/{@code while}/{@code .forEach(} loop whose body calls an
     * <em>external</em> outbound port (a {@code ...domain.ports.out.*Port}
     * type — persistence {@code *Repository} ports are deliberately excluded
     * because they are local DB calls atomic with the tx).
     */
    @Test
    void noTransactionalMethodWithLoopCallingOutboundPort() throws IOException {
        List<Path> serviceRoots = applicationServiceRoots();
        List<String> offenders = new ArrayList<>();
        for (Path root : serviceRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> scanForTransactionalLoopPortCall(p, offenders));
            }
        }
        assertThat(offenders)
                .as("@Transactional methods on @Service/@Component classes in application/service/ "
                        + "must NOT contain a for/while/.forEach loop whose body calls an external "
                        + "outbound port (com.gameplatform.*.domain.ports.out.*Port — *Repository "
                        + "ports are excluded as local DB). This is the poison-isolation invariant "
                        + "fixed in FASE 6 (BUG-SYNC-01 / C-01)")
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private void scanForTransactionalLoopPortCall(Path file, List<String> offenders) {
        String raw;
        try {
            raw = Files.readString(file);
        } catch (IOException e) {
            return;
        }
        String src = stripComments(raw);

        // Only @Service / @Component classes are in scope.
        if (!hasAnnotation(src, "Service") && !hasAnnotation(src, "Component")) {
            return;
        }
        if (!src.contains("@Transactional")) {
            return;
        }

        // Collect EXTERNAL port types (simple name ends with "Port", not
        // "Repository") imported by this file, then field names whose declared
        // type is one of those ports.
        Set<String> externalPortTypes = collectExternalPortTypes(src);
        if (externalPortTypes.isEmpty()) {
            return;
        }
        Set<String> portFieldNames = collectPortFieldNames(src, externalPortTypes);
        if (portFieldNames.isEmpty()) {
            return;
        }

        // Extract each @Transactional method body, then within it extract each
        // loop body, and check whether the loop body calls an external port.
        for (String methodBody : extractTransactionalMethodBodies(src)) {
            for (String loopBody : extractLoopBodies(methodBody)) {
                if (referencesPort(loopBody, portFieldNames)) {
                    offenders.add(projectRoot().relativize(file).toString());
                    return; // one offence per file is enough
                }
            }
        }
    }

    /**
     * Strip block ({@code /* ... *&#47;}) and line ({@code // ...}) comments.
     * We do not strip inside string literals — for the patterns we check, an
     * over-eager strip in strings (e.g. a string containing {@code //}) is
     * harmless because we only check structural annotations/keywords, not
     * string contents.
     */
    private String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("(?m)//.*$", "");
    }

    private boolean hasAnnotation(String src, String annotation) {
        return Pattern.compile("@" + annotation + "\\b").matcher(src).find();
    }

    /**
     * Collect simple class names of <em>external</em> outbound port types
     * imported by this file. External = simple name ends with {@code Port}
     * (e.g. {@code PushUserToLocalServersPort}, {@code PublishGameStatePort},
     * {@code SyncCentralSystemPort}). Persistence ports named
     * {@code *Repository} are deliberately excluded — they are local DB
     * operations atomic with the caller's tx and not the poison-isolation
     * concern of FASE 6.
     */
    private Set<String> collectExternalPortTypes(String src) {
        Set<String> portTypes = new HashSet<>();
        Pattern importP = Pattern.compile(
                "import\\s+com\\.gameplatform(?:\\.[a-z_][a-z0-9_]*)*"
                        + "\\.domain\\.ports\\.out\\.[A-Za-z0-9_]+\\s*;");
        Matcher m = importP.matcher(src);
        while (m.find()) {
            String line = m.group();
            String simple = line.substring(line.lastIndexOf('.') + 1)
                                .replace(";", "").trim();
            if (simple.endsWith("Port")) {
                portTypes.add(simple);
            }
        }
        return portTypes;
    }

    /**
     * Collect field names declared with a port type (e.g.
     * {@code private final OutboxEventRepository outboxEventRepository;}).
     */
    private Set<String> collectPortFieldNames(String src, Set<String> portTypes) {
        Set<String> names = new HashSet<>();
        Pattern fieldP = Pattern.compile(
                "(?:private|protected|public)\\s+(?:final\\s+)?(\\w+)\\s+(\\w+)\\s*;");
        Matcher m = fieldP.matcher(src);
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            if (portTypes.contains(type)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Find every {@code @Transactional} occurrence in {@code src}, advance to
     * the next method-opening brace at paren-depth 0 (skipping the annotation
     * argument list, other annotations, modifiers, return type, parameters,
     * and {@code throws} clause), then balance braces to extract the method
     * body. Class-level {@code @Transactional} annotations are skipped by
     * detecting {@code class}/{@code interface}/{@code enum}/{@code record}
     * keywords between the annotation and the brace.
     */
    private List<String> extractTransactionalMethodBodies(String src) {
        List<String> bodies = new ArrayList<>();
        Matcher m = Pattern.compile("@Transactional\\b").matcher(src);
        while (m.find()) {
            int i = m.end();
            int parenDepth = 0;
            int braceOpen = -1;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (c == '{' && parenDepth == 0) {
                    braceOpen = i;
                    break;
                }
                i++;
            }
            if (braceOpen < 0) continue;
            String sigPart = src.substring(m.end(), braceOpen);
            if (sigPart.contains(" class ")
                    || sigPart.contains(" interface ")
                    || sigPart.contains(" enum ")
                    || sigPart.contains(" record ")) {
                // class-level @Transactional — skip; we only police methods.
                continue;
            }
            // Balance braces to find the matching close.
            int j = braceOpen;
            int depth = 0;
            int bodyEnd = -1;
            while (j < src.length()) {
                char c = src.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { bodyEnd = j; break; }
                }
                j++;
            }
            if (bodyEnd < 0) continue;
            bodies.add(src.substring(braceOpen + 1, bodyEnd));
        }
        return bodies;
    }

    /**
     * Extract the body of every loop contained in {@code methodBody}.
     * Handles {@code for (...)}, {@code while (...)} and {@code .forEach(...)}.
     * For brace-block bodies the balanced contents between {@code {} } are
     * returned; for single-statement loops the text up to the next {@code ;}
     * is returned. Lambda {@code .forEach(x -> expr)} single-expression forms
     * are returned as the expression text up to the enclosing delimiter.
     */
    private List<String> extractLoopBodies(String methodBody) {
        List<String> bodies = new ArrayList<>();
        Pattern loopStart = Pattern.compile("\\bfor\\s*\\(|\\bwhile\\s*\\(|\\.forEach\\s*\\(");
        Matcher m = loopStart.matcher(methodBody);
        while (m.find()) {
            int openParenIdx = m.end() - 1; // index of '('
            int closeParenIdx = findMatchingCloseParen(methodBody, openParenIdx);
            if (closeParenIdx < 0) continue;
            // After the close paren, skip whitespace; expect '{' for a block
            // body. If not '{', fall back to single-statement extraction up to
            // the next ';' (covers `for (...) doThing();` and
            // `.forEach(x -> doThing());`).
            int i = closeParenIdx + 1;
            while (i < methodBody.length() && Character.isWhitespace(methodBody.charAt(i))) {
                i++;
            }
            if (i >= methodBody.length()) continue;
            if (methodBody.charAt(i) != '{') {
                int semi = methodBody.indexOf(';', i);
                if (semi < 0) semi = methodBody.length();
                bodies.add(methodBody.substring(i, semi));
                continue;
            }
            // Balance braces from i to find the loop body.
            int depth = 0;
            int bodyStart = i + 1;
            int bodyEnd = -1;
            while (i < methodBody.length()) {
                char c = methodBody.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { bodyEnd = i; break; }
                }
                i++;
            }
            if (bodyEnd < 0) continue;
            bodies.add(methodBody.substring(bodyStart, bodyEnd));
        }
        return bodies;
    }

    private int findMatchingCloseParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private boolean referencesPort(String body, Set<String> portFieldNames) {
        for (String fn : portFieldNames) {
            if (body.contains(fn + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<Path> outAdapterRoots() {
        Path root = projectRoot();
        return List.of(
                root.resolve("central-system/src/main/java/com/gameplatform/central/infrastructure/adapters/out"),
                root.resolve("local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out")
        );
    }

    private List<Path> applicationServiceRoots() {
        Path root = projectRoot();
        return List.of(
                root.resolve("central-system/src/main/java/com/gameplatform/central/application/service"),
                root.resolve("local-server/src/main/java/com/gameplatform/local/application/service")
        );
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
