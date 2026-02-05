package com.project.curve.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpExtractor Test")
class ClientIpExtractorTest {

    @AfterEach
    void tearDown() {
        // Reset RequestContext after each test
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getClientIp() - RequestContext based")
    class GetClientIpFromContextTest {

        @Test
        @DisplayName("Should extract valid client IP")
        void extractValidClientIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("Should extract IPv6 address correctly")
        void extractIpv6Address() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        }

        @Test
        @DisplayName("Should return default IP if RequestContext is missing")
        void noRequestContext_shouldReturnDefaultIp() {
            // Given
            RequestContextHolder.resetRequestAttributes();

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is null")
        void nullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(null);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is empty string")
        void emptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is 'unknown'")
        void unknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("unknown");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is 'UNKNOWN' (case insensitive)")
        void unknownUppercaseRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("UNKNOWN");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("getClientIp(HttpServletRequest) - Direct request provided")
    class GetClientIpFromRequestTest {

        @Test
        @DisplayName("Should extract valid client IP")
        void extractValidClientIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.5");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("10.0.0.5");
        }

        @Test
        @DisplayName("Should return default IP if request is null")
        void nullRequest_shouldReturnDefaultIp() {
            // When
            String clientIp = ClientIpExtractor.getClientIp(null);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is null")
        void nullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(null);

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is empty string")
        void emptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return default IP if remoteAddr is 'unknown'")
        void unknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("unknown");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("getDefaultIp() Test")
    class GetDefaultIpTest {

        @Test
        @DisplayName("Default IP should be 127.0.0.1")
        void getDefaultIp() {
            // When
            String defaultIp = ClientIpExtractor.getDefaultIp();

            // Then
            assertThat(defaultIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("Proxy Environment Simulation")
    class ProxyEnvironmentTest {

        @Test
        @DisplayName("Should return actual client IP after ForwardedHeaderFilter processing")
        void forwardedHeaderFilterProcessed() {
            // Given: After ForwardedHeaderFilter processed X-Forwarded-For
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.42");  // Actual client IP

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("203.0.113.42");
        }

        @Test
        @DisplayName("Should work correctly behind load balancer")
        void behindLoadBalancer() {
            // Given: AWS ALB, Nginx etc.
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("172.16.0.10");  // After LB processing

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("172.16.0.10");
        }
    }

    @Nested
    @DisplayName("Exception Handling Test")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("Should return default IP when exception occurs")
        void exceptionDuringExtraction_shouldReturnDefaultIp() {
            // Given: Request that throws exception
            HttpServletRequest faultyRequest = new HttpServletRequest() {
                @Override
                public String getRemoteAddr() {
                    throw new RuntimeException("Simulated error");
                }

                // Other methods (unused)
                @Override
                public Object getAttribute(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getAttributeNames() {
                    return null;
                }

                @Override
                public String getCharacterEncoding() {
                    return null;
                }

                @Override
                public void setCharacterEncoding(String env) {
                }

                @Override
                public int getContentLength() {
                    return 0;
                }

                @Override
                public long getContentLengthLong() {
                    return 0;
                }

                @Override
                public String getContentType() {
                    return null;
                }

                @Override
                public jakarta.servlet.ServletInputStream getInputStream() {
                    return null;
                }

                @Override
                public String getParameter(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getParameterNames() {
                    return null;
                }

                @Override
                public String[] getParameterValues(String name) {
                    return null;
                }

                @Override
                public java.util.Map<String, String[]> getParameterMap() {
                    return null;
                }

                @Override
                public String getProtocol() {
                    return null;
                }

                @Override
                public String getScheme() {
                    return null;
                }

                @Override
                public String getServerName() {
                    return null;
                }

                @Override
                public int getServerPort() {
                    return 0;
                }

                @Override
                public java.io.BufferedReader getReader() {
                    return null;
                }

                @Override
                public String getRemoteHost() {
                    return null;
                }

                @Override
                public void setAttribute(String name, Object o) {
                }

                @Override
                public void removeAttribute(String name) {
                }

                @Override
                public java.util.Locale getLocale() {
                    return null;
                }

                @Override
                public java.util.Enumeration<java.util.Locale> getLocales() {
                    return null;
                }

                @Override
                public boolean isSecure() {
                    return false;
                }

                @Override
                public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return null;
                }

                @Override
                public int getRemotePort() {
                    return 0;
                }

                @Override
                public String getLocalName() {
                    return null;
                }

                @Override
                public String getLocalAddr() {
                    return null;
                }

                @Override
                public int getLocalPort() {
                    return 0;
                }

                @Override
                public jakarta.servlet.ServletContext getServletContext() {
                    return null;
                }

                @Override
                public jakarta.servlet.AsyncContext startAsync() {
                    return null;
                }

                @Override
                public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) {
                    return null;
                }

                @Override
                public boolean isAsyncStarted() {
                    return false;
                }

                @Override
                public boolean isAsyncSupported() {
                    return false;
                }

                @Override
                public jakarta.servlet.AsyncContext getAsyncContext() {
                    return null;
                }

                @Override
                public jakarta.servlet.DispatcherType getDispatcherType() {
                    return null;
                }

                @Override
                public String getRequestId() {
                    return null;
                }

                @Override
                public String getProtocolRequestId() {
                    return null;
                }

                @Override
                public jakarta.servlet.ServletConnection getServletConnection() {
                    return null;
                }

                @Override
                public String getAuthType() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.Cookie[] getCookies() {
                    return null;
                }

                @Override
                public long getDateHeader(String name) {
                    return 0;
                }

                @Override
                public String getHeader(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getHeaders(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getHeaderNames() {
                    return null;
                }

                @Override
                public int getIntHeader(String name) {
                    return 0;
                }

                @Override
                public String getMethod() {
                    return null;
                }

                @Override
                public String getPathInfo() {
                    return null;
                }

                @Override
                public String getPathTranslated() {
                    return null;
                }

                @Override
                public String getContextPath() {
                    return null;
                }

                @Override
                public String getQueryString() {
                    return null;
                }

                @Override
                public String getRemoteUser() {
                    return null;
                }

                @Override
                public boolean isUserInRole(String role) {
                    return false;
                }

                @Override
                public java.security.Principal getUserPrincipal() {
                    return null;
                }

                @Override
                public String getRequestedSessionId() {
                    return null;
                }

                @Override
                public String getRequestURI() {
                    return null;
                }

                @Override
                public StringBuffer getRequestURL() {
                    return null;
                }

                @Override
                public String getServletPath() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.HttpSession getSession(boolean create) {
                    return null;
                }

                @Override
                public jakarta.servlet.http.HttpSession getSession() {
                    return null;
                }

                @Override
                public String changeSessionId() {
                    return null;
                }

                @Override
                public boolean isRequestedSessionIdValid() {
                    return false;
                }

                @Override
                public boolean isRequestedSessionIdFromCookie() {
                    return false;
                }

                @Override
                public boolean isRequestedSessionIdFromURL() {
                    return false;
                }

                @Override
                public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) {
                    return false;
                }

                @Override
                public void login(String username, String password) {
                }

                @Override
                public void logout() {
                }

                @Override
                public java.util.Collection<jakarta.servlet.http.Part> getParts() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.Part getPart(String name) {
                    return null;
                }

                @Override
                public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
                    return null;
                }
            };

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(faultyRequest));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }
}
