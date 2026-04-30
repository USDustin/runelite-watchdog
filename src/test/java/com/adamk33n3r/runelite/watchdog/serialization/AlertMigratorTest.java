package com.adamk33n3r.runelite.watchdog.serialization;

import com.adamk33n3r.runelite.watchdog.Version;
import com.adamk33n3r.runelite.watchdog.WatchdogConfig;
import com.adamk33n3r.runelite.watchdog.alerts.*;
import com.adamk33n3r.runelite.watchdog.notifications.Overlay;
import com.adamk33n3r.runelite.watchdog.notifications.ScreenFlash;
import com.adamk33n3r.runelite.watchdog.notifications.Sound;

import net.runelite.api.Skill;
import net.runelite.client.config.FlashNotification;

import org.junit.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AlertMigratorTest {

    private final AlertMigrator migrator = new AlertMigrator();

    private List<Alert> listOf(Alert alert) {
        return new ArrayList<>(List.of(alert));
    }

    // ── 2.4.0: StatDrain → StatChanged ──────────────────────────────────────

    @Test
    public void migrate_2_4_0_convertsStatDrainToStatChanged() {
        StatDrainAlert drain = new StatDrainAlert("Drain Test");
        drain.setSkill(Skill.ATTACK);
        drain.setDrainAmount(5);
        List<Alert> alerts = listOf(drain);

        migrator.migrate(alerts, new Version("2.3.0"));

        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0) instanceof StatChangedAlert);
        StatChangedAlert changed = (StatChangedAlert) alerts.get(0);
        assertEquals(-5, changed.getChangedAmount());
        assertEquals(Skill.ATTACK, changed.getSkill());
        assertEquals("Drain Test", changed.getName());
    }

    @Test
    public void migrate_2_4_0_scalesAudioGainFromMinimum() {
        ChatAlert alert = new ChatAlert("Gain Test");
        Sound sound = new Sound();
        sound.setGain(-25);
        alert.addNotification(sound);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.3.0"));

        assertEquals(0, sound.getGain());
    }

    @Test
    public void migrate_2_4_0_scalesAudioGainFromMaximum() {
        ChatAlert alert = new ChatAlert("Gain Test");
        Sound sound = new Sound();
        sound.setGain(5);
        alert.addNotification(sound);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.3.0"));

        assertEquals(10, sound.getGain());
    }

    // ── 2.8.0: ScreenFlash property migration ───────────────────────────────

    @Test
    public void migrate_2_8_0_convertsSolidTwoSeconds() {
        ChatAlert alert = new ChatAlert("Flash Test");
        ScreenFlash flash = new ScreenFlash();
        flash.setFlashNotification(FlashNotification.SOLID_TWO_SECONDS);
        alert.addNotification(flash);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.7.0"));

        assertEquals(FlashMode.SOLID, flash.getFlashMode());
        assertEquals(2, flash.getFlashDuration());
        assertNull(flash.getFlashNotification());
    }

    @Test
    public void migrate_2_8_0_convertsSolidUntilCancelled() {
        ChatAlert alert = new ChatAlert("Flash Test");
        ScreenFlash flash = new ScreenFlash();
        flash.setFlashNotification(FlashNotification.SOLID_UNTIL_CANCELLED);
        alert.addNotification(flash);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.7.0"));

        assertEquals(FlashMode.SOLID, flash.getFlashMode());
        assertEquals(0, flash.getFlashDuration());
        assertNull(flash.getFlashNotification());
    }

    @Test
    public void migrate_2_8_0_convertsFlashTwoSeconds() {
        ChatAlert alert = new ChatAlert("Flash Test");
        ScreenFlash flash = new ScreenFlash();
        flash.setFlashNotification(FlashNotification.FLASH_TWO_SECONDS);
        alert.addNotification(flash);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.7.0"));

        assertEquals(FlashMode.FLASH, flash.getFlashMode());
        assertEquals(2, flash.getFlashDuration());
        assertNull(flash.getFlashNotification());
    }

    // ── 2.13.0: Overlay default text color ──────────────────────────────────

    @Test
    public void migrate_2_13_0_setsDefaultTextColorWhenNull() {
        ChatAlert alert = new ChatAlert("Overlay Test");
        Overlay overlay = new Overlay();
        overlay.setTextColor(null);
        alert.addNotification(overlay);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.12.0"));

        assertEquals(WatchdogConfig.DEFAULT_NOTIFICATION_TEXT_COLOR, overlay.getTextColor());
    }

    @Test
    public void migrate_2_13_0_doesNotOverwriteExistingTextColor() {
        ChatAlert alert = new ChatAlert("Overlay Test");
        Overlay overlay = new Overlay();
        overlay.setTextColor(Color.RED);
        alert.addNotification(overlay);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, new Version("2.12.0"));

        assertEquals(Color.RED, overlay.getTextColor());
    }

    // ── 3.14.0: Pattern anchoring ────────────────────────────────────────────

    @Test
    public void migrate_3_14_0_anchorsGlobWithOnlyLeadingWildcard() {
        ChatAlert alert = new ChatAlert("Pattern Test");
        alert.setPattern("*the end.");
        alert.setRegexEnabled(false);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertTrue(alert.isRegexEnabled());
        assertTrue(alert.getPattern().startsWith("^") && alert.getPattern().endsWith("$"));
    }

    @Test
    public void migrate_3_14_0_anchorsGlobWithOnlyTrailingWildcard() {
        ChatAlert alert = new ChatAlert("Pattern Test");
        alert.setPattern("The beginning*");
        alert.setRegexEnabled(false);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertTrue(alert.isRegexEnabled());
        assertTrue(alert.getPattern().startsWith("^") && alert.getPattern().endsWith("$"));
    }

    @Test
    public void migrate_3_14_0_stripsOuterWildcardsWithoutAnchoring() {
        PlayerChatAlert alert = new PlayerChatAlert("Pattern Test");
        alert.setPattern("*and*");
        alert.setRegexEnabled(false);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertFalse(alert.isRegexEnabled());
        assertEquals("and", alert.getPattern());
    }

    @Test
    public void migrate_3_14_0_anchorsExistingRegexMissingBothAnchors() {
        ChatAlert alert = new ChatAlert("Pattern Test");
        alert.setPattern(".*the end.");
        alert.setRegexEnabled(true);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertTrue(alert.getPattern().startsWith("^") && alert.getPattern().endsWith("$"));
    }

    @Test
    public void migrate_3_14_0_addsLeadingAnchorToRegexMissingIt() {
        ChatAlert alert = new ChatAlert("Pattern Test");
        alert.setPattern("The beginning and the end\\.$");
        alert.setRegexEnabled(true);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertTrue(alert.getPattern().startsWith("^"));
        assertTrue(alert.getPattern().endsWith("$"));
    }

    @Test
    public void migrate_3_14_0_doesNotModifyFullyAnchoredRegex() {
        ChatAlert alert = new ChatAlert("Pattern Test");
        alert.setPattern("^The beginning and the end\\.$");
        alert.setRegexEnabled(true);

        migrator.migrate(listOf(alert), new Version("3.13.0"));

        assertEquals("^The beginning and the end\\.$", alert.getPattern());
    }

    // ── Unknown version guard (see ADR-0001) ─────────────────────────────────

    @Test
    public void migrate_unknownVersion_doesNotConvertStatDrainAlert() {
        StatDrainAlert drain = new StatDrainAlert("Drain Test");
        List<Alert> alerts = listOf(drain);

        migrator.migrate(alerts, null);

        assertTrue(alerts.get(0) instanceof StatDrainAlert);
    }

    @Test
    public void migrate_unknownVersion_doesNotCorruptAlreadyMigratedScreenFlash() {
        ChatAlert alert = new ChatAlert("Flash Test");
        ScreenFlash flash = new ScreenFlash();
        flash.setFlashMode(FlashMode.SOLID);
        flash.setFlashDuration(5);
        flash.setFlashNotification(null);
        alert.addNotification(flash);
        List<Alert> alerts = listOf(alert);

        migrator.migrate(alerts, null);

        assertEquals(FlashMode.SOLID, flash.getFlashMode());
        assertEquals(5, flash.getFlashDuration());
    }
}
