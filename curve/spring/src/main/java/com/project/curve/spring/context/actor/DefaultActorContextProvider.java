package com.project.curve.spring.context.actor;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import com.project.curve.spring.util.ClientIpExtractor;

/**
 * Default Actor Context Provider.
 *
 * <p>Default provider used in environments without Spring Security.
 * Treats all requests as SYSTEM user and collects client IP information.</p>
 *
 * <h3>Client IP Handling</h3>
 * <p>When using Spring Boot's ForwardedHeaderFilter, request.getRemoteAddr()
 * automatically processes the X-Forwarded-For header to return the correct client IP.</p>
 *
 * <h3>Security Configuration (Recommended)</h3>
 * <pre>
 * # application.yml
 * server:
 *   forward-headers-strategy: framework  # Enable Spring Boot's ForwardedHeaderFilter
 *   tomcat:
 *     remoteip:
 *       internal-proxies: 10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}
 *       protocol-header: X-Forwarded-Proto
 *       remote-ip-header: X-Forwarded-For
 * </pre>
 *
 * @see ClientIpExtractor
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.use-behind-a-proxy-server">Spring Boot Proxy Configuration</a>
 */
public class DefaultActorContextProvider implements ActorContextProvider {

    private static final String SYSTEM_USER = "SYSTEM";
    private static final String SYSTEM_ROLE = "ROLE_SYSTEM";

    @Override
    public EventActor getActor() {
        return new EventActor(SYSTEM_USER, SYSTEM_ROLE, ClientIpExtractor.getClientIp());
    }
}
