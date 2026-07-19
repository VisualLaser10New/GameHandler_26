package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.local.infrastructure.security.LocalAdminBuildingAuthorizationManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Controller REST per la registrazione dei dispositivi di gioco tramite
 * certificati TLS mutui. Genera certificati firmati dalla CA locale per
 * i dispositivi pre-autorizzati nel catalogo dell'edificio.
 *
 * @see LocalAdminBuildingAuthorizationManager
 */
@RestController
@RequestMapping("/api/devices")
@PreAuthorize("hasRole('LOCAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
public class DeviceRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistrationController.class);

    private final GameJpaRepository gameRepository;
    private final ResourceLoader resourceLoader;
    private final LocalAdminBuildingAuthorizationManager authorizationManager;
    private final String buildingId;

    @Value("${ssl.ca-cert-path:classpath:ca.crt}")
    private String caCertPath;

    @Value("${ssl.ca-key-path:classpath:ca.key}")
    private String caKeyPath;

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /**
     * Costruisce il controller con il repository dei giochi, il caricatore
     * di risorse, il gestore delle autorizzazioni e l'identificativo dell'edificio.
     *
     * @param gameRepository repository JPA dei giochi
     * @param resourceLoader caricatore di risorse per i certificati CA
     * @param authorizationManager gestore delle autorizzazioni per l'admin locale
     * @param buildingId identificativo dell'edificio
     */
    public DeviceRegistrationController(GameJpaRepository gameRepository,
                                        ResourceLoader resourceLoader,
                                        LocalAdminBuildingAuthorizationManager authorizationManager,
                                        @Value("${app.building-id}") String buildingId) {
        this.gameRepository = gameRepository;
        this.resourceLoader = resourceLoader;
        this.authorizationManager = authorizationManager;
        this.buildingId = buildingId;
    }

    /**
     * Registra un dispositivo firmando una Certificate Signing Request (CSR)
     * con la CA locale. Il dispositivo deve essere pre-autorizzato nel
     * catalogo dell'edificio.
     *
     * @param request mappa contenente i campi "gameId" e "csr" (PEM)
     * @return una {@link ResponseEntity} con il certificato firmato e il certificato CA,
     *         o status 400/403/500 in caso di errore
     * @throws org.springframework.security.access.AccessDeniedException se il gioco non appartiene all'edificio
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(@RequestBody Map<String, String> request) {
        ensureAuthorized();

        String gameId = request.get("gameId");
        String csrPem = request.get("csr");

        if (gameId == null || csrPem == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gameId and csr are required"));
        }

        // Verify if game exists in catalog AND belongs to this admin's building
        Optional<GameJpaEntity> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) {
            log.warn("Device registration rejected: Game ID {} not found in catalog", gameId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Device is not pre-authorized in the catalog"));
        }
        if (!buildingId.equals(gameOpt.get().getBuildingId())) {
            log.warn("Device registration rejected: Game ID {} belongs to building {} (admin building: {})",
                    gameId, gameOpt.get().getBuildingId(), buildingId);
            throw new org.springframework.security.access.AccessDeniedException(
                    "Game does not belong to admin's building");
        }

        log.info("Registering device: Game ID = {}, name = {}", gameId, gameOpt.get().getName());

        try {
            // Parse the CSR
            PEMParser pemParser = new PEMParser(new StringReader(csrPem));
            Object parsedObj = pemParser.readObject();
            if (!(parsedObj instanceof PKCS10CertificationRequest csr)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid CSR PEM format"));
            }

            // Load CA Cert and CA Key
            X509Certificate caCert = loadCaCertificate();
            PrivateKey caPrivateKey = loadCaPrivateKey();

            // Sign CSR
            String clientCertPem = signCsr(csr, caCert, caPrivateKey);
            String caCertPem = getPemEncoded(caCert);

            return ResponseEntity.ok(Map.of(
                    "certificate", clientCertPem,
                    "caCertificate", caCertPem
            ));

        } catch (Exception e) {
            log.error("Failed to sign CSR for gameId {}: {}", gameId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sign certificate: " + e.getMessage()));
        }
    }

    /**
     * Verifica che l'amministratore autenticato sia autorizzato a gestire
     * l'edificio corrente.
     *
     * @throws org.springframework.security.access.AccessDeniedException se non autorizzato
     */
    private void ensureAuthorized() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authorizationManager.canManageBuilding(authentication)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Local admin is not authorized to manage building " + buildingId);
        }
    }

    /**
     * Carica il certificato della CA dal percorso configurato.
     *
     * @return il certificato X.509 della CA
     * @throws Exception in caso di errore di caricamento
     */
    private X509Certificate loadCaCertificate() throws Exception {
        try (InputStream in = resourceLoader.getResource(caCertPath).getInputStream()) {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    /**
     * Carica la chiave privata della CA dal percorso configurato.
     *
     * @return la chiave privata della CA
     * @throws Exception in caso di errore di caricamento o formato non supportato
     */
    private PrivateKey loadCaPrivateKey() throws Exception {
        try (InputStream in = resourceLoader.getResource(caKeyPath).getInputStream()) {
            PEMParser pemParser = new PEMParser(new InputStreamReader(in));
            Object parsedObj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (parsedObj instanceof PEMKeyPair pemKeyPair) {
                return converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            } else if (parsedObj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                return converter.getPrivateKey(pki);
            } else {
                throw new IllegalArgumentException("Unknown CA private key format: " + parsedObj.getClass().getName());
            }
        }
    }

    /**
     * Firma una Certificate Signing Request (CSR) utilizzando il certificato
     * e la chiave privata della CA, producendo un certificato cliente.
     *
     * @param csr la richiesta di firma del certificato
     * @param caCert il certificato della CA
     * @param caPrivateKey la chiave privata della CA
     * @return il certificato firmato in formato PEM
     * @throws Exception in caso di errore durante la firma
     */
    private String signCsr(PKCS10CertificationRequest csr, X509Certificate caCert, PrivateKey caPrivateKey) throws Exception {
        X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        
        BigInteger serialNumber = new BigInteger(64, new java.security.SecureRandom());
        
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 10);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 730);
        
        X500Name subject = csr.getSubject();

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        java.security.PublicKey clientPublicKey = converter.getPublicKey(csr.getSubjectPublicKeyInfo());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                clientPublicKey
        );

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        
        String cn = subject.toString();
        if (cn.contains("CN=")) {
            String gameId = cn.substring(cn.indexOf("CN=") + 3);
            if (gameId.contains(",")) {
                gameId = gameId.substring(0, gameId.indexOf(","));
            }
            GeneralNames subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, gameId));
            certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(caPrivateKey);
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate clientCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

        return getPemEncoded(clientCert);
    }

    /**
     * Codifica un certificato X.509 nel formato PEM.
     *
     * @param cert il certificato da codificare
     * @return la stringa in formato PEM
     * @throws Exception in caso di errore di codifica
     */
    private String getPemEncoded(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pemWriter = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            pemWriter.writeObject(cert);
        }
        return sw.toString();
    }
}
