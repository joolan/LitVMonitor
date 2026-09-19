package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.UserMemo;
import com.litv.monitor.service.UserMemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/memo")
@RequiredArgsConstructor
public class UserMemoController {

    private final UserMemoService userMemoService;

    @GetMapping
    public Result<UserMemo> getMemo() {
        String username = getCurrentUsername();
        UserMemo memo = userMemoService.getMemo(username);
        return Result.success(memo);
    }

    @PostMapping
    public Result<UserMemo> saveMemo(@RequestBody Map<String, String> body) {
        String username = getCurrentUsername();
        String content = body.getOrDefault("content", "");
        UserMemo memo = userMemoService.saveMemo(username, content);
        return Result.success(memo);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
