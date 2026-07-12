package com.gameplatform.shared.domain.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical application roles of the Boardgame Platform.
 *
 * <p>Replaces the legacy {@code "USER"} / {@code "ADMIN"} string literals with four
 * domain-specific roles:
 * <ul>
 *   <li>{@link #PLAYER} — participates in matches, views personal statistics and tournaments.</li>
 *   <li>{@link #LOCAL_ADMIN} — manages games, devices and sessions of an assigned building.</li>
 *   <li>{@link #GAME_ADMIN} — defines game types and match registration rules.</li>
 *   <li>{@link #PLATFORM_ADMIN} — manages users, buildings and global statistics.</li>
 * </ul>
 *
 * <p>Pure domain type: no framework annotations. Tolerant parsing ({@link #parse})
 * recognises legacy literals emitted before the migration ({@code USER} →
 * {@code PLAYER}, {@code ADMIN} → {@code PLATFORM_ADMIN}) and transparently strips an
 * optional {@code ROLE_} prefix, so that JWTs and rows produced before FASE 0 of
 * {@code PIANO_UTENTI_TORNEI.md} keep authorising correctly (compatibility window,
 * see §7). Strict lookup ({@link #of}) rejects unknown and legacy names and is intended
 * for role-assignment validation in later phases.</p>
 *
 * <p>{@link #toAuthorityNames} derives Spring Security authority names
 * ({@code "ROLE_" + name()}) from a role claim, applying the legacy mapping; it is the
 * single entry point used by both {@code central-system}'s and {@code local-server}'s
 * JWT filters, guaranteeing a consistent authority surface across the platform.</p>
 */
public enum Role {
    PLAYER,
    LOCAL_ADMIN,
    GAME_ADMIN,
    PLATFORM_ADMIN;

    /** Legacy literal that maps to {@link #PLAYER}. */
    private static final String LEGACY_PLAYER = "USER";
    /** Legacy literal that maps to {@link #PLATFORM_ADMIN}. */
    private static final String LEGACY_PLATFORM_ADMIN = "ADMIN";
    /** Spring Security authority prefix. */
    private static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * Strict single-name lookup. Recognises only canonical names (case-insensitive),
     * <strong>not</strong> legacy aliases and <strong>not</strong> the {@code ROLE_}
     * prefix. Intended for validating role assignment, not for parsing JWT claims.
     *
     * @param name the canonical role literal (e.g. {@code "PLAYER"})
     * @return the matching {@link Role}
     * @throws IllegalArgumentException if {@code name} is blank, unknown or a legacy alias
     */
    public static Role of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be null or blank");
        }
        String trimmed = name.trim();
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(trimmed)) {
                return role;
            }
        }
        throw new IllegalArgumentException(
                "Unknown role: '" + trimmed + "'. Valid values are: " + Arrays.toString(values()));
    }

    /**
     * Tolerant parsing of a CSV role string into a {@link Set} of canonical {@link Role}s.
     *
     * <p>Accepts legacy aliases ({@code USER} → {@code PLAYER}, {@code ADMIN} →
     * {@code PLATFORM_ADMIN}) and strips an optional {@code ROLE_} prefix. Blank and
     * null tokens are skipped. Unknown non-blank tokens are skipped (no exception) so
     * that legacy or custom claims never invalidate otherwise valid JWTs.</p>
     *
     * @param csv nullable comma-separated list of role literals
     * @return an unmodifiable, de-duplicated {@link Set} of recognised roles (never {@code null})
     */
    public static Set<Role> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        return parseTokens(Arrays.asList(csv.split(",")));
    }

    /**
     * Tolerant parsing of a role collection (e.g. a JWT {@code roles} claim) into a
     * {@link Set} of canonical {@link Role}s. See {@link #parse(String)} for the full
     * mapping rules.
     *
     * @param roles nullable role literals
     * @return an unmodifiable, de-duplicated {@link Set} of recognised roles (never {@code null})
     */
    public static Set<Role> parse(Iterable<String> roles) {
        if (roles == null) {
            return Collections.emptySet();
        }
        return parseTokens(roles);
    }

    private static Set<Role> parseTokens(Iterable<String> tokens) {
        Set<Role> resolved = new LinkedHashSet<>();
        for (String raw : tokens) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalised = trimmed;
            if (normalised.toUpperCase(Locale.ROOT).startsWith(AUTHORITY_PREFIX)) {
                normalised = normalised.substring(AUTHORITY_PREFIX.length());
            }
            Role mapped = mapLegacy(normalised);
            if (mapped != null) {
                resolved.add(mapped);
                continue;
            }
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(normalised)) {
                    resolved.add(role);
                    break;
                }
            }
        }
        return Collections.unmodifiableSet(resolved);
    }

    /**
     * Maps legacy aliases to canonical roles; returns {@code null} for non-legacy tokens.
     */
    private static Role mapLegacy(String token) {
        if (LEGACY_PLAYER.equalsIgnoreCase(token)) {
            return PLAYER;
        }
        if (LEGACY_PLATFORM_ADMIN.equalsIgnoreCase(token)) {
            return PLATFORM_ADMIN;
        }
        return null;
    }

    /**
     * Formats a collection of roles as a comma-separated string of canonical names,
     * preserving insertion order and de-duplicating.
     *
     * @param roles nullable collection
     * @return a CSV string (empty if {@code roles} is null or empty)
     */
    public static String format(Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        Set<Role> seen = new LinkedHashSet<>(roles);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Role role : seen) {
            if (!first) {
                sb.append(',');
            }
            sb.append(role.name());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Derives Spring Security authority names ({@code "ROLE_" + name()}) from a CSV role
     * claim, applying the legacy mapping. Single entry point used by the platform's JWT
     * filters to guarantee a consistent authority surface.
     *
     * @param csv nullable comma-separated role claim
     * @return an unmodifiable list of authority names (never {@code null})
     */
    public static List<String> toAuthorityNames(String csv) {
        return toAuthorityNames(parse(csv));
    }

    /**
     * Derives Spring Security authority names ({@code "ROLE_" + name()}) from a role claim
     * collection, applying the legacy mapping. See {@link #toAuthorityNames(String)}.
     *
     * @param roles nullable role literals
     * @return an unmodifiable list of authority names (never {@code null})
     */
    public static List<String> toAuthorityNames(Iterable<String> roles) {
        return toAuthorityNames(parse(roles));
    }

    private static List<String> toAuthorityNames(Set<Role> roles) {
        List<String> authorities = new ArrayList<>(roles.size());
        for (Role role : roles) {
            authorities.add(AUTHORITY_PREFIX + role.name());
        }
        return Collections.unmodifiableList(authorities);
    }
}