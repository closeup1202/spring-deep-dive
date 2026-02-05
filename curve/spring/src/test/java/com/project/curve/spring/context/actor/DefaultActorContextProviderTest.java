package com.project.curve.spring.context.actor;

import com.project.curve.core.envelope.EventActor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultActorContextProvider Test")
class DefaultActorContextProviderTest {

    @Mock
    private HttpServletRequest request;

    private DefaultActorContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultActorContextProvider();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("User Info Test")
    class UserInfoTest {

        @Test
        @DisplayName("Should always return SYSTEM user")
        void getActor_shouldReturnSystemUser() {
            // Given
            setUpRequestContext("192.168.1.100");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.id()).isEqualTo("SYSTEM");
            assertThat(actor.role()).isEqualTo("ROLE_SYSTEM");
        }
    }

    @Nested
    @DisplayName("Client IP Test")
    class ClientIpTest {

        @Test
        @DisplayName("Should return remoteAddr if Request Context exists")
        void getActor_withRequestContext_shouldReturnRemoteAddr() {
            // Given
            setUpRequestContext("10.0.0.1");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP (127.0.0.1) if Request Context does not exist")
        void getActor_withoutRequestContext_shouldReturnDefaultIp() {
            // Given - No request context set

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is null")
        void getActor_withNullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext(null);

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is empty string")
        void getActor_withEmptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext("");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is 'unknown'")
        void getActor_withUnknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext("unknown");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return IPv6 address correctly")
        void getActor_withIpv6Address_shouldReturnIpv6() {
            // Given
            setUpRequestContext("::1");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("::1");
        }

        @Test
        @DisplayName("Should return IP processed by X-Forwarded-For correctly")
        void getActor_withForwardedIp_shouldReturnForwardedIp() {
            // Given - IP after ForwardedHeaderFilter processing
            setUpRequestContext("203.0.113.42");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("203.0.113.42");
        }
    }

    @Nested
    @DisplayName("Consistency Test")
    class ConsistencyTest {

        @Test
        @DisplayName("Should return consistent results when called multiple times")
        void getActor_calledMultipleTimes_shouldReturnConsistentResults() {
            // Given
            setUpRequestContext("192.168.1.1");

            // When
            EventActor actor1 = provider.getActor();
            EventActor actor2 = provider.getActor();

            // Then
            assertThat(actor1.id()).isEqualTo(actor2.id());
            assertThat(actor1.role()).isEqualTo(actor2.role());
            assertThat(actor1.ip()).isEqualTo(actor2.ip());
        }
    }

    private void setUpRequestContext(String remoteAddr) {
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }
}
