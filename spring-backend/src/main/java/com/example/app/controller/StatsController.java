package com.example.app.controller;

import com.example.app.dto.stats.StatsResponse;
import com.example.app.model.AppUser;
import com.example.app.service.StatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse getStats(@AuthenticationPrincipal AppUser user) {
        return statsService.getStats(user);
    }
}
