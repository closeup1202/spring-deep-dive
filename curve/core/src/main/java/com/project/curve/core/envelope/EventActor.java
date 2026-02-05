package com.project.curve.core.envelope;

public record EventActor(
        String id,   // userId or systemId
        String role, // user role
        String ip    // client ip
) {
}
