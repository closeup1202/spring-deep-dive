package com.exam.testpractice.controller;

import com.exam.testpractice.domain.Member;
import com.exam.testpractice.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/members")
    public ResponseEntity<Member> signup(@RequestParam String email, @RequestParam String name) {
        return ResponseEntity.ok(memberService.signup(email, name));
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<Member> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMember(id));
    }
}
