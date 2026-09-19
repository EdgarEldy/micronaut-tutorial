package com.edgareldy.micronauttutorial.security;

import io.micronaut.core.io.ResourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no context) of the failure modes of JwtKeyFactory.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class JwtKeyFactoryTest {

    private final JwtKeyFactory factory = new JwtKeyFactory();
    private final ResourceResolver resolver = new ResourceResolver();

    private IllegalStateException failWith(String priv, String pub) {
        return assertThrows(IllegalStateException.class, () -> factory.rsaSignatureGenerator(
                new JwtProperties(priv, pub, "issuer", 60), resolver));
    }

    @Test
    void blankPrivateLocationFailsClearly() {
        IllegalStateException e = failWith("  ", "file:dev-keys/publicKey.pem");
        assertTrue(e.getMessage().contains("app.jwt.private-key-location"), e.getMessage());
    }

    @Test
    void nullPublicLocationFailsClearly() {
        IllegalStateException e = failWith("file:dev-keys/privateKey.pem", null);
        assertTrue(e.getMessage().contains("app.jwt.public-key-location"), e.getMessage());
    }

    @Test
    void missingFileFailsClearly() {
        IllegalStateException e = failWith("file:dev-keys/nope.pem", "file:dev-keys/publicKey.pem");
        assertTrue(e.getMessage().contains("not found") && e.getMessage().contains("nope.pem"), e.getMessage());
    }

    @Test
    void pkcs1PrivateKeyIsRejectedWithAConversionHint(@TempDir Path dir) throws Exception {
        Path pkcs1 = dir.resolve("pkcs1.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        IllegalStateException e = failWith("file:" + pkcs1, "file:dev-keys/publicKey.pem");
        assertTrue(e.getMessage().contains("PKCS#1") && e.getMessage().contains("pkcs8"), e.getMessage());
    }
}
