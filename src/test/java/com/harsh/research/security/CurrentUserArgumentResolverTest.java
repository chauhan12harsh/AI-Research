package com.harsh.research.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserArgumentResolverTest {

    static class ControllerParameters {
        void annotatedUser(@CurrentUser UUID userId) {}
        void ordinaryPathVariable(UUID workspaceId) {}
        void annotatedPrincipal(@CurrentUser AuthPrincipal principal) {}
        void unsupportedAnnotated(@CurrentUser String value) {}
    }

    private final CurrentUserArgumentResolver resolver =
            new CurrentUserArgumentResolver();

    @Test
    void supportsOnlyExplicitlyAnnotatedSupportedTypes() throws Exception {
        MethodParameter user = new MethodParameter(
                ControllerParameters.class.getDeclaredMethod(
                        "annotatedUser", UUID.class
                ), 0
        );
        MethodParameter pathVariable = new MethodParameter(
                ControllerParameters.class.getDeclaredMethod(
                        "ordinaryPathVariable", UUID.class
                ), 0
        );
        MethodParameter principal = new MethodParameter(
                ControllerParameters.class.getDeclaredMethod(
                        "annotatedPrincipal", AuthPrincipal.class
                ), 0
        );
        MethodParameter unsupported = new MethodParameter(
                ControllerParameters.class.getDeclaredMethod(
                        "unsupportedAnnotated", String.class
                ), 0
        );

        assertTrue(resolver.supportsParameter(user));
        assertTrue(resolver.supportsParameter(principal));
        assertFalse(resolver.supportsParameter(pathVariable));
        assertFalse(resolver.supportsParameter(unsupported));
    }
}
