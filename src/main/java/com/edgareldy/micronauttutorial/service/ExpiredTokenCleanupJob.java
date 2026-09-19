package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.repository.ActivationTokenRepository;
import com.edgareldy.micronauttutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.micronauttutorial.repository.PasswordResetTokenRepository;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Daily housekeeping: deletes blacklisted, activation and password reset tokens past their expiry.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
public class ExpiredTokenCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredTokenCleanupJob.class);

    private final BlacklistedTokenRepository blacklistedTokens;
    private final ActivationTokenRepository activationTokens;
    private final PasswordResetTokenRepository resetTokens;

    public ExpiredTokenCleanupJob(BlacklistedTokenRepository blacklistedTokens, ActivationTokenRepository activationTokens,
                                  PasswordResetTokenRepository resetTokens) {
        this.blacklistedTokens = blacklistedTokens;
        this.activationTokens = activationTokens;
        this.resetTokens = resetTokens;
    }

    /**
     * Deletes every expired token and returns how many rows were removed per kind.
     * Public so tests can call it directly instead of waiting for the cron.
     */
    // @Scheduled is Micronaut's built-in task scheduler: the annotation is processed at compile time and the
    // method is registered to run on the cron expression (Quartz style, seconds first: every day at 03:00),
    // with no runtime scanning. It runs on a scheduler thread outside any HTTP request, hence its own
    // transaction here.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public Result purgeExpired() {
        Instant now = Instant.now();
        Result result = new Result(blacklistedTokens.deleteByExpiresAtBefore(now),
                activationTokens.deleteByExpiresAtBefore(now), resetTokens.deleteByExpiryDateBefore(now));
        LOG.info("Expired token cleanup: {} blacklisted, {} activation, {} password reset", result.blacklisted(),
                result.activation(), result.passwordReset());
        return result;
    }

    /**
     * Number of rows deleted by one run, per token kind.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    public record Result(long blacklisted, long activation, long passwordReset) {
    }
}
