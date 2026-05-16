package ee.ut.eventticketing.api_gateway.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthDataInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AuthDataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = authProperties.bootstrapAdmin();
        if (admin == null || !admin.enabled()) {
            return;
        }

        String username = admin.username().trim().toLowerCase();
        userAccountRepository.existsByUsername(username)
                .filter(exists -> !exists)
                .flatMap(ignored -> userAccountRepository.save(new UserAccount(
                        username,
                        admin.email().trim().toLowerCase(),
                        passwordEncoder.encode(admin.password()),
                        "CUSTOMER,ADMIN")))
                .block();
    }
}
