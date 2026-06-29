package com.gameplatform.client.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to dynamically enroll a game client with the Local Server's CA.
 * Generates an RSA keypair and a CSR, submits it to the Local Server,
 * and saves the returned certificate/CA certificate to a PKCS12 keystore.
 */
public class CertificateEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(CertificateEnrollmentService.class);

    private final String gameId;
    private final String localServerUrl;
    private final File certsDir;
    private final File keystoreFile;
    private final File truststoreFile;

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public CertificateEnrollmentService(String gameId, String localServerUrl) {
        this.gameId = gameId;
        this.localServerUrl = localServerUrl;
        this.certsDir = new File("certs");
        this.keystoreFile = new File(certsDir, gameId + "-keystore.p12");
        this.truststoreFile = new File(certsDir, "local-truststore.p12");
    }

    /**
     * Performs enrollment if the keystore does not exist.
     * Returns true if enrollment succeeded or was already done.
     */
    public boolean enrollIfNecessary() {
        if (keystoreFile.exists() && truststoreFile.exists()) {
            log.info("Client certificates already exist at {} and {}", keystoreFile.getPath(), truststoreFile.getPath());
            return true;
        }

        log.info("Client certificates missing. Starting dynamic enrollment for game: {}", gameId);
        try {
            if (!certsDir.exists()) {
                certsDir.mkdirs();
            }

            // 1. Generate KeyPair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            PrivateKey clientPrivateKey = kp.getPrivate();
            PublicKey clientPublicKey = kp.getPublic();

            // 2. Generate CSR
            X500Name subject = new X500Name("CN=" + gameId + ",O=GamePlatformLocal,C=IT");
            PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                    subject, clientPublicKey);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(clientPrivateKey);
            PKCS10CertificationRequest csr = p10Builder.build(signer);

            StringWriter sw = new StringWriter();
            try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pemWriter = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
                pemWriter.writeObject(csr);
            }
            String csrPem = sw.toString();

            // 3. Send to Local Server
            log.info("Sending CSR to Local Server at {}/api/devices/register", localServerUrl);
            Map<String, String> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("csr", csrPem);

            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(payload);

            // Bypass SSL for the enrollment request to avoid bootstrapping chicken-and-egg trust issues
            SSLContext trustAllCtx = SSLContext.getInstance("TLS");
            trustAllCtx.init(null, new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            }, new java.security.SecureRandom());

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(trustAllCtx)
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(localServerUrl + "/api/devices/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                log.error("Failed to enroll certificate. Status: {}, Response: {}", httpResponse.statusCode(), httpResponse.body());
                return false;
            }

            // 4. Parse Response
            Map<?, ?> responseMap = mapper.readValue(httpResponse.body(), Map.class);
            String clientCertPem = (String) responseMap.get("certificate");
            String caCertPem = (String) responseMap.get("caCertificate");

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate clientCert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(clientCertPem.getBytes()));
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(caCertPem.getBytes()));

            // 5. Create Keystore (.p12)
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null, null);
            keystore.setKeyEntry("client-key", clientPrivateKey, "changeit".toCharArray(),
                    new Certificate[] { clientCert, caCert });
            try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                keystore.store(fos, "changeit".toCharArray());
            }

            // 6. Create Truststore (.p12)
            KeyStore truststore = KeyStore.getInstance("PKCS12");
            truststore.load(null, null);
            truststore.setCertificateEntry("local-ca", caCert);
            try (FileOutputStream fos = new FileOutputStream(truststoreFile)) {
                truststore.store(fos, "changeit".toCharArray());
            }

            log.info("Dynamic enrollment complete. Created keystore and truststore in local certs directory.");
            return true;

        } catch (Exception e) {
            log.error("Error performing dynamic certificate enrollment", e);
            return false;
        }
    }

    public File getKeystoreFile() {
        return keystoreFile;
    }

    public File getTruststoreFile() {
        return truststoreFile;
    }
}
