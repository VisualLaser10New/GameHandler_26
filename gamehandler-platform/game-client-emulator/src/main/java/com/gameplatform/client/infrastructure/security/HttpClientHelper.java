package com.gameplatform.client.infrastructure.security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Helper to build an HttpClient instance.
 * If the local server URL is HTTPS, it attempts to load the dynamic truststore
 * so it can securely verify the Local Server certificate.
 */
public class HttpClientHelper {

    private static volatile String token;

    public static void setToken(String t) {
        token = t;
    }

    public static String getToken() {
        return token;
    }

    public static HttpClient getHttpClient(String localServerUrl) {
        if (localServerUrl.startsWith("https://")) {
            try {
                File truststoreFile = new File("certs/local-truststore.p12");
                if (truststoreFile.exists()) {
                    KeyStore trustStore = KeyStore.getInstance("PKCS12");
                    try (InputStream in = new FileInputStream(truststoreFile)) {
                        trustStore.load(in, "changeit".toCharArray());
                    }
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);

                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                    return HttpClient.newBuilder()
                            .sslContext(sslContext)
                            .build();
                } else {
                    // Try to load from resources as a fallback
                    try (InputStream in = HttpClientHelper.class.getResourceAsStream("/local-truststore.p12")) {
                        if (in != null) {
                            KeyStore trustStore = KeyStore.getInstance("PKCS12");
                            trustStore.load(in, "changeit".toCharArray());
                            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                            tmf.init(trustStore);

                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                            return HttpClient.newBuilder()
                                    .sslContext(sslContext)
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback to default HttpClient
            }
        }
        return HttpClient.newHttpClient();
    }
}
