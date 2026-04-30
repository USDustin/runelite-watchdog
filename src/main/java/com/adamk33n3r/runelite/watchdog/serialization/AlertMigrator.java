package com.adamk33n3r.runelite.watchdog.serialization;

import com.adamk33n3r.runelite.watchdog.Util;
import com.adamk33n3r.runelite.watchdog.Version;
import com.adamk33n3r.runelite.watchdog.WatchdogConfig;
import com.adamk33n3r.runelite.watchdog.alerts.*;
import com.adamk33n3r.runelite.watchdog.notifications.IAudioNotification;
import com.adamk33n3r.runelite.watchdog.notifications.Overlay;
import com.adamk33n3r.runelite.watchdog.notifications.ScreenFlash;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.FlashNotification;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class AlertMigrator {

    /**
     * Migrates {@code alerts} in-place from {@code fromVersion} up to current.
     *
     * <p>{@code fromVersion} may be {@code null} when importing user-supplied JSON with no version
     * envelope. In that case only idempotent migrations run (≥ 2.13.0) — see ADR-0001.
     *
     * <p>When adding a new migration: classify it here. If running it on already-migrated data would
     * corrupt values, guard it with {@code versionKnown} exactly as 2.4.0 and 2.8.0 are guarded.
     */
    public void migrate(List<Alert> alerts, @Nullable Version fromVersion) {
        boolean versionKnown = fromVersion != null;
        if (versionKnown && fromVersion.compareTo(new Version("2.4.0")) < 0) {
            log.debug("Migrating: StatDrainAlert → StatChangedAlert, gain scale");
            this.migrate_2_4_0(alerts);
        }
        if (versionKnown && fromVersion.compareTo(new Version("2.8.0")) < 0) {
            log.debug("Migrating: ScreenFlash property rename");
            this.migrate_2_8_0(alerts);
        }
        if (!versionKnown || fromVersion.compareTo(new Version("2.13.0")) < 0) {
            log.debug("Migrating: Overlay default text color");
            this.migrate_2_13_0(alerts);
        }
        if (!versionKnown || fromVersion.compareTo(new Version("3.14.0")) < 0) {
            log.debug("Migrating: Pattern matcher anchoring");
            this.migrate_3_14_0(alerts);
        }
    }

    private void migrate_2_4_0(List<Alert> alerts) {
        alerts.replaceAll(alert -> {
            if (alert instanceof StatDrainAlert) {
                StatDrainAlert statDrainAlert = (StatDrainAlert) alert;
                StatChangedAlert statChangedAlert = new StatChangedAlert();
                statChangedAlert.setName(statDrainAlert.getName());
                statChangedAlert.setEnabled(statDrainAlert.isEnabled());
                statChangedAlert.setDebounceTime(statDrainAlert.getDebounceTime());
                statChangedAlert.setSkill(statDrainAlert.getSkill());
                statChangedAlert.setChangedAmount(-statDrainAlert.getDrainAmount());
                if (statChangedAlert.getNotifications() != null && statDrainAlert.getNotifications() != null) {
                    statChangedAlert.getNotifications().addAll(statDrainAlert.getNotifications());
                }
                return statChangedAlert;
            }
            return alert;
        });

        alerts.stream()
            .filter(alert -> !(alert instanceof AlertGroup) && !(alert instanceof AdvancedAlert))
            .flatMap(alert -> Objects.requireNonNull(alert.getNotifications()).stream())
            .filter(notification -> notification instanceof IAudioNotification)
            .map(notification -> (IAudioNotification) notification)
            .forEach(sound -> sound.setGain(Util.scale(sound.getGain(), -25, 5, 0, 10)));
    }

    private void migrate_2_8_0(List<Alert> alerts) {
        alerts.stream()
            .filter(alert -> !(alert instanceof AlertGroup) && !(alert instanceof AdvancedAlert))
            .flatMap(alert -> Objects.requireNonNull(alert.getNotifications()).stream())
            .filter(notification -> notification instanceof ScreenFlash)
            .map(notification -> (ScreenFlash) notification)
            .forEach(screenFlash -> {
                FlashNotification oldEnum = screenFlash.getFlashNotification();
                screenFlash.setFlashMode((oldEnum == FlashNotification.SOLID_TWO_SECONDS || oldEnum == FlashNotification.SOLID_UNTIL_CANCELLED) ? FlashMode.SOLID : FlashMode.FLASH);
                screenFlash.setFlashDuration((oldEnum == FlashNotification.FLASH_TWO_SECONDS || oldEnum == FlashNotification.SOLID_TWO_SECONDS) ? 2 : 0);
                screenFlash.setFlashNotification(null);
            });
    }

    private void migrate_2_13_0(List<Alert> alerts) {
        alerts.stream()
            .filter(alert -> !(alert instanceof AlertGroup) && !(alert instanceof AdvancedAlert))
            .flatMap(alert -> Objects.requireNonNull(alert.getNotifications()).stream())
            .filter(notification -> notification instanceof Overlay)
            .map(notification -> (Overlay) notification)
            .forEach(overlay -> {
                if (overlay.getTextColor() == null) {
                    overlay.setTextColor(WatchdogConfig.DEFAULT_NOTIFICATION_TEXT_COLOR);
                }
            });
    }

    private void migrate_3_14_0(List<Alert> alerts) {
        this.flatten(alerts)
            .filter(alert -> alert instanceof RegexMatcher)
            .map(alert -> (RegexMatcher) alert)
            .forEach(alert -> {
                String prevPattern = alert.getPattern();
                if (!prevPattern.isEmpty()) {
                    this.upgrade_3_14_0_patterns(
                        alert::getPattern,
                        alert::isRegexEnabled,
                        alert::setPattern,
                        alert::setRegexEnabled
                    );
                    log.debug("Migrated pattern from '{}' to '{}'", prevPattern, alert.getPattern());
                }
                if (alert instanceof OverheadTextAlert) {
                    OverheadTextAlert overheadTextAlert = (OverheadTextAlert) alert;
                    if (overheadTextAlert.getNpcName().isEmpty()) {
                        return;
                    }
                    this.upgrade_3_14_0_patterns(
                        overheadTextAlert::getNpcName,
                        overheadTextAlert::isNpcRegexEnabled,
                        overheadTextAlert::setNpcName,
                        overheadTextAlert::setNpcRegexEnabled
                    );
                }
            });
    }

    private void upgrade_3_14_0_patterns(
        Supplier<String> patternSupplier,
        Supplier<Boolean> regexEnabledSupplier,
        Consumer<String> patternSave,
        Consumer<Boolean> regexEnabledSave
    ) {
        String pattern = patternSupplier.get();
        if (!regexEnabledSupplier.get()) {
            if (!pattern.startsWith("*") || !pattern.endsWith("*")) {
                pattern = Util.createRegexFromGlob(pattern);
                regexEnabledSave.accept(true);
            } else if (pattern.length() > 1) {
                pattern = pattern.substring(1, pattern.length() - 1);
            }
        }

        if (regexEnabledSupplier.get()) {
            if (!pattern.startsWith("^")) {
                pattern = "^" + pattern;
            }
            if (!pattern.endsWith("$")) {
                pattern = pattern + "$";
            }
        }

        patternSave.accept(pattern);
    }

    private Stream<Alert> flatten(List<Alert> alerts) {
        return alerts.stream().flatMap(alert -> {
            if (alert instanceof AlertGroup) {
                return this.flatten(((AlertGroup) alert).getAlerts());
            }
            return Stream.of(alert);
        });
    }
}
