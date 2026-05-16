package ee.ut.eventticketing.api_gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.auth")
public record AuthProperties(BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(
            boolean enabled,
            String username,
            String email,
            String password) {
    }
}
