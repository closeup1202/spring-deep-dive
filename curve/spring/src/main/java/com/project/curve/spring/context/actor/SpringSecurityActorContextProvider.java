package com.project.curve.spring.context.actor;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import com.project.curve.spring.util.ClientIpExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security-based Actor Context Provider.
 * <p>
 * Creates EventActor using Spring Security authentication information.
 * Unauthenticated requests are treated as SYSTEM user.
 * <p>
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
public class SpringSecurityActorContextProvider implements ActorContextProvider {

    @Override
    public EventActor getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return new EventActor("SYSTEM", "ROLE_SYSTEM", ClientIpExtractor.getClientIp());
        }

        String userId = auth.getName();

        // Extract authority information (first authority)
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        return new EventActor(userId, role, ClientIpExtractor.getClientIp());
    }
}
