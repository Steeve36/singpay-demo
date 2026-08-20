package com.demo.singpay.config;

import com.demo.singpay.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final RefreshTokenService refreshTokenService;

    public ScheduledTasks(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    // Tous les jours à 3h du matin — purge les refresh tokens expirés et révoqués
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredRefreshTokens() {
        log.info("Purge des refresh tokens expirés/révoqués...");
        refreshTokenService.purgeExpired();
        log.info("Purge terminée.");
    }
}
