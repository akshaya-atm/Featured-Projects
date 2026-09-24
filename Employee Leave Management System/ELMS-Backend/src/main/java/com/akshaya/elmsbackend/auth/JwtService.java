package com.akshaya.elmsbackend.auth;

import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl-seconds}") long ttlSeconds) {

        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Access token lifetime must be positive"
            );
        }

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = Duration.ofSeconds(ttlSeconds);
    }

    public String generateAccessToken(String employeeId){
        Instant now = Instant.now();
        JwtClaimsSet claims  = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(employeeId)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();

    }

}
