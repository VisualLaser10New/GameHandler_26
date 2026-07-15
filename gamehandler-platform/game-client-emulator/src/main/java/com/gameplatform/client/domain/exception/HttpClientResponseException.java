package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Raised by {@link ApiClient} when the Local Server responds with a
 * {@code 4xx} status other than 401/403 (e.g. 400 Bad Request, 404 Not
 * Found, 409 Conflict). Carries the numeric {@code statusCode} and the
 * raw response {@code body} so the UI layer can render a domain-specific,
 * human-readable message instead of a bare {@code "HTTP <code>"} string.
 * <p>
 * {@link #getMessage()} keeps the previous {@code "HTTP <code> — body=<body>"}
 * format so the existing string-sniffing branches in other views keep working.
 */
public class HttpClientResponseException extends RuntimeException {
    private final int statusCode;
    private final String body;

    public HttpClientResponseException(int statusCode, String body) {
        super("HTTP " + statusCode + " — body=" + (body == null ? "" : body));
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
