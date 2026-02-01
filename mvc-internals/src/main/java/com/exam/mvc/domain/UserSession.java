package com.exam.mvc.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class UserSession {
    private Long id;
    private String username;
    private String role;
}
