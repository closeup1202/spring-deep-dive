package com.project.curve.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client IP address extraction utility.
 * <p>
 * Uses Spring Boot's ForwardedHeaderFilter to extract the correct client IP
 * even in proxy environments.
 * <p>
 * <b>Client IP Handling:</b>
 * <ul>
 *   <li>When Spring Boot's ForwardedHeaderFilter is enabled,
 *       request.getRemoteAddr() automatically handles X-Forwarded-For headers</li>
 *   <li>For security, only uses Spring-validated remoteAddr instead of reading headers directly</li>
 *   <li>Returns default IP (127.0.0.1) when request context is unavailable or error occurs</li>
 * </ul>
 * <p>
 * <b>Security Configuration (Recommended):</b>
 * <pre>
 * # application.yml
 * server:
 *   forward-headers-strategy: framework  # Enables Spring Boot's ForwardedHeaderFilter
 *   tomcat:
 *     remoteip:
 *       internal-proxies: 10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}
 *       protocol-header: X-Forwarded-Proto
 *       remote-ip-header: X-Forwarded-For
 * </pre>
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.use-behind-a-proxy-server">Spring Boot Proxy Configuration</a>
 */
@Slf4j
public final class ClientIpExtractor {

    private static final String DEFAULT_IP = "127.0.0.1";
    private static final String UNKNOWN_IP = "unknown";

    private ClientIpExtractor() {
        // Utility class - prevent instantiation
    }

    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                log.debug("No request context available, using default IP");
                return DEFAULT_IP;
            }

            HttpServletRequest request = attributes.getRequest();
            String remoteAddr = request.getRemoteAddr();

            if (remoteAddr != null && !remoteAddr.isEmpty() && !UNKNOWN_IP.equalsIgnoreCase(remoteAddr)) {
                return remoteAddr;
            }

            log.warn("Remote address is null or unknown, using default IP");
            return DEFAULT_IP;

        } catch (Exception e) {
            log.error("Failed to extract client IP, using default IP", e);
            return DEFAULT_IP;
        }
    }

    /**
     * Extracts the client IP address from the specified HttpServletRequest.
     * <p>
     * Use this when directly providing a request object in tests or special cases.
     *
     * @param request HTTP request
     * @return Client IP address (returns "127.0.0.1" on extraction failure)
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            log.debug("Request is null, using default IP");
            return DEFAULT_IP;
        }

        try {
            String remoteAddr = request.getRemoteAddr();

            if (remoteAddr != null && !remoteAddr.isEmpty() && !UNKNOWN_IP.equalsIgnoreCase(remoteAddr)) {
                return remoteAddr;
            }

            log.warn("Remote address is null or unknown, using default IP");
            return DEFAULT_IP;

        } catch (Exception e) {
            log.error("Failed to extract client IP from request, using default IP", e);
            return DEFAULT_IP;
        }
    }

    public static String getDefaultIp() {
        return DEFAULT_IP;
    }
}
