package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventActor 테스트")
class EventActorTest {

    @Test
    @DisplayName("정상적인 EventActor 생성")
    void createValidEventActor() {
        // given
        String id = "user-123";
        String role = "ROLE_USER";
        String ip = "192.168.1.1";

        // when
        EventActor actor = new EventActor(id, role, ip);

        // then
        assertNotNull(actor);
        assertEquals(id, actor.id());
        assertEquals(role, actor.role());
        assertEquals(ip, actor.ip());
    }

    @Test
    @DisplayName("EventActor - null 값들로 생성 가능 (validation 없음)")
    void createEventActorWithNullValues() {
        // when
        EventActor actor = new EventActor(null, null, null);

        // then - validation이 없으므로 생성 성공
        assertNotNull(actor);
        assertNull(actor.id());
        assertNull(actor.role());
        assertNull(actor.ip());
    }
}
