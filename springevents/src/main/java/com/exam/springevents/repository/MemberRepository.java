package com.exam.springevents.repository;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MemberRepository {
    private final List<String> members = new ArrayList<>();

    public void save(String name) {
        members.add(name);
    }

    public boolean exists(String name) {
        return members.contains(name);
    }
    
    public void clear() {
        members.clear();
    }
}