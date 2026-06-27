package com.gameplatform.local.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
class JwtConfigTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource privateResource;

    @Mock
    private Resource publicResource;

    private final String privateKeyPath = "classpath:private.pem";
    private final String publicKeyPath = "classpath:public.pem";

    private String validPrivateKeyPem;
    private String validPublicKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        // Generate valid RSA keys
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        validPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n" 
                + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) 
                + "\n-----END PRIVATE KEY-----";

        validPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" 
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) 
                + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void testLoadKeys_Success() throws Exception {
        when(resourceLoader.getResource(privateKeyPath)).thenReturn(privateResource);
        when(resourceLoader.getResource(publicKeyPath)).thenReturn(publicResource);

        when(privateResource.exists()).thenReturn(true);
        when(publicResource.exists()).thenReturn(true);

        when(privateResource.getInputStream()).thenReturn(new ByteArrayInputStream(validPrivateKeyPem.getBytes()));
        when(publicResource.getInputStream()).thenReturn(new ByteArrayInputStream(validPublicKeyPem.getBytes()));

        JwtConfig jwtConfig = new JwtConfig(resourceLoader, privateKeyPath, publicKeyPath);

        assertThat(jwtConfig.jwtTokenProvider()).isNotNull();
        assertThat(jwtConfig.jwtTokenValidator()).isNotNull();
    }

    @Test
    void testLoadKeys_PrivateKeyMissing() {
        when(resourceLoader.getResource(privateKeyPath)).thenReturn(privateResource);
        when(resourceLoader.getResource(publicKeyPath)).thenReturn(publicResource);

        when(privateResource.exists()).thenReturn(false);

        assertThatThrownBy(() -> new JwtConfig(resourceLoader, privateKeyPath, publicKeyPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local private key PEM file not found at " + privateKeyPath);
    }

    @Test
    void testLoadKeys_PublicKeyMissing() {
        when(resourceLoader.getResource(privateKeyPath)).thenReturn(privateResource);
        when(resourceLoader.getResource(publicKeyPath)).thenReturn(publicResource);

        when(privateResource.exists()).thenReturn(true);
        when(publicResource.exists()).thenReturn(false);

        assertThatThrownBy(() -> new JwtConfig(resourceLoader, privateKeyPath, publicKeyPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local public key PEM file not found at " + publicKeyPath);
    }

    @Test
    void testLoadKeys_MalformedPrivateKey() throws Exception {
        when(resourceLoader.getResource(privateKeyPath)).thenReturn(privateResource);
        when(resourceLoader.getResource(publicKeyPath)).thenReturn(publicResource);

        when(privateResource.exists()).thenReturn(true);
        when(publicResource.exists()).thenReturn(true);

        // Return malformed content for private key (invalid base64 or not a key spec)
        when(privateResource.getInputStream()).thenReturn(new ByteArrayInputStream("invalid-key-content".getBytes()));

        assertThatThrownBy(() -> new JwtConfig(resourceLoader, privateKeyPath, publicKeyPath))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load or parse local RSA keys");
    }
}
