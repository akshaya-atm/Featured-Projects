package com.akshaya.elmsbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JWTConfig {
    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret}") String base64Secret) {

        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 decoded bytes"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSecretKey)
        );
    }
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${app.jwt.issuer}") String issuer) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(issuer)
        );

        return decoder;
    }
}