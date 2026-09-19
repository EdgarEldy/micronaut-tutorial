package com.edgareldy.micronauttutorial.security;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGenerator;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RS256 key pair from the configured locations and exposes it to Micronaut Security.
 * The single {@link RSASignatureGenerator} bean is used both to sign tokens and to validate them.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// A @Factory is a bean that produces other beans through @Bean methods. It is used here because
// RSASignatureGenerator is a library class we cannot annotate. Micronaut Security looks up the bean
// named "generator" to sign JWTs; as it also implements the signature configuration, the same bean is
// picked up by the validator, so signing and validation always use the same pair.
@Factory
public class JwtKeyFactory {

    @Singleton
    @Named("generator")
    public RSASignatureGenerator rsaSignatureGenerator(JwtProperties properties, ResourceResolver resolver) {
        RSAPrivateKey privateKey = (RSAPrivateKey) read(properties.privateKeyLocation(),
                "app.jwt.private-key-location (env JWT_PRIVATE_KEY_LOCATION)", resolver, true);
        RSAPublicKey publicKey = (RSAPublicKey) read(properties.publicKeyLocation(),
                "app.jwt.public-key-location (env JWT_PUBLIC_KEY_LOCATION)", resolver, false);
        return new RSASignatureGenerator(new RSASignatureGeneratorConfiguration() {
            @Override
            public RSAPrivateKey getPrivateKey() {
                return privateKey;
            }

            @Override
            public JWSAlgorithm getJwsAlgorithm() {
                return JWSAlgorithm.RS256;
            }

            @Override
            public RSAPublicKey getPublicKey() {
                return publicKey;
            }
        });
    }

    private static Object read(String location, String property, ResourceResolver resolver, boolean isPrivate) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Missing JWT configuration: " + property + " must be set");
        }
        try (InputStream in = resolver.getResourceAsStream(location).orElseThrow(() ->
                new IllegalStateException("JWT key file not found: " + location + " (from " + property + ")"))) {
            String pem = new String(in.readAllBytes()).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return isPrivate ? kf.generatePrivate(new PKCS8EncodedKeySpec(der))
                    : kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot read JWT key at " + location + " (from " + property + "): "
                    + e.getMessage(), e);
        }
    }
}
