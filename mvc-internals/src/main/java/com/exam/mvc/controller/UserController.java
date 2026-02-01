package com.exam.mvc.controller;

import com.exam.mvc.annotation.LoginUser;
import com.exam.mvc.domain.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class UserController {

    @GetMapping("/me")
    public String getMe(@LoginUser UserSession user) {
        // HttpServletRequest 지옥에서 해방!
        // Spring이 알아서 user 객체를 꽂아줌
        
        if (user == null) {
            return "Guest User";
        }
        
        log.info("Controller received user: {}", user);
        return "Hello, " + user.getUsername() + "! (ID: " + user.getId() + ")";
    }
}
