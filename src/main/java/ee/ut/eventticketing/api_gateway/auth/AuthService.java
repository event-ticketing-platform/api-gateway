package ee.ut.eventticketing.api_gateway.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ee.ut.eventticketing.api_gateway.AuthController;
import ee.ut.eventticketing.api_gateway.security.JwtProperties;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public Mono<AuthController.AuthResponse> register(AuthController.RegisterRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        validateRegistration(username, email, request.password());

        return userAccountRepository.existsByUsername(username)
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken"));
                    }
                    return userAccountRepository.existsByEmail(email);
                })
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered"));
                    }
                    UserAccount account = new UserAccount(
                            username,
                            email,
                            passwordEncoder.encode(request.password()),
                            "CUSTOMER");
                    return userAccountRepository.save(account);
                })
                .map(this::toAuthResponse);
    }

    public Mono<AuthController.AuthResponse> login(AuthController.LoginRequest request) {
        String username = normalize(request.username());
        return userAccountRepository.findByUsername(username)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")))
                .map(this::toAuthResponse);
    }

    public Mono<AuthController.ProfileResponse> profile(String username) {
        return userAccountRepository.findByUsername(normalize(username))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")))
                .map(account -> new AuthController.ProfileResponse(
                        account.getId(),
                        account.getUsername(),
                        account.getEmail(),
                        parseRoles(account.getRoles())));
    }

    private AuthController.AuthResponse toAuthResponse(UserAccount account) {
        List<String> roles = parseRoles(account.getRoles());
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.ttlMinutes() * 60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(account.getUsername())
                .claim("roles", roles)
                .claim("scope", "booking:read booking:write payment:read payment:write")
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();

        return new AuthController.AuthResponse(
                account.getId(),
                token,
                "Bearer",
                expiresAt.toString(),
                account.getUsername(),
                account.getEmail(),
                roles);
    }

    private void validateRegistration(String username, String email, String password) {
        if (username.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at least 3 characters");
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return List.of(roles.split(",")).stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
