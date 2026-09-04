package com.lmguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import com.lmguard.config.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI at {@code /swagger-ui/index.html}.
 *
 * <p>Registers the bearer scheme globally so the "Authorize" button works: paste the token
 * from {@code POST /api/auth/login} once and every secured endpoint becomes callable from
 * the browser.
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    private final AppProperties appProperties;

    @Bean
    public OpenAPI lmGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LM-GUARD API")
                        .version("0.1.0")
                        .description("""
                                **LM-GUARD** - AI-assisted, evidence-first Legal Metrology inspection platform.

                                ### How a decision is made
                                1. An inspector uploads a package image.
                                2. The AI/OCR service extracts **facts** - what is visible, and where.
                                3. A **deterministic rule engine** decides compliance from those facts.
                                4. Every finding is backed by **evidence**: an image region and a confidence.
                                5. A transparent weighted model produces a **risk score**.
                                6. The **human inspector** makes the enforcement decision.

                                No language model interprets the law at any point. A finding the system is
                                not confident about is reported as `INCONCLUSIVE`, never as a violation.

                                ### Rules disclaimer
                                The rules shipped with this build are clearly marked **demo/sample rules**.
                                They are not the Legal Metrology Act or Rules, and are not legally verified.

                                ### Authentication
                                Call `POST /api/auth/login`, then send `Authorization: Bearer <token>`.
                                """)
                        .contact(new Contact().name("LM-GUARD Team"))
                        .license(new License().name("Proprietary - internal use")))
                .servers(List.of(
                        new Server().url(appProperties.baseUrl()).description("Configured server")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token returned by POST /api/auth/login")));
    }
}
