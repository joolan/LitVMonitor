package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.service.StatusPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/status")
@RequiredArgsConstructor
public class StatusPageController {

    private final StatusPageService statusPageService;

    @GetMapping
    public Result<Map<String, Object>> getStatus() {
        if (!statusPageService.isPublicAccessAllowed()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAnonymous = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal());
            if (isAnonymous) {
                return Result.error(403, "状态页未公开访问");
            }
        }
        return Result.success(statusPageService.getStatusPageData());
    }
}
