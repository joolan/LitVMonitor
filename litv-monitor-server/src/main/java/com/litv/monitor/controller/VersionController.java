package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VersionController {

    @Value("${app.version:1.0.0}")
    private String backendVersion;

    @Value("${app.frontend-version:1.0.0}")
    private String frontendVersion;

    @GetMapping("/version")
    public Result<Map<String, String>> getVersion() {
        return Result.success(Map.of(
                "backend", backendVersion,
                "frontend", frontendVersion
        ));
    }
}
