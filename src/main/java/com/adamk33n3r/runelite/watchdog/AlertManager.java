package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.nodegraph.nodes.ActionNode;
import com.adamk33n3r.nodegraph.nodes.TriggerNode;
import com.adamk33n3r.runelite.watchdog.alerts.*;
import com.adamk33n3r.runelite.watchdog.serialization.AlertMigrator;
import com.adamk33n3r.runelite.watchdog.serialization.WatchdogGsonFactory;

import com.adamk33n3r.runelite.watchdog.elevenlabs.ElevenLabs;
import com.adamk33n3r.runelite.watchdog.notifications.*;
import com.adamk33n3r.runelite.watchdog.notifications.tts.TTSSource;
import com.adamk33n3r.runelite.watchdog.notifications.tts.Voice;
import com.adamk33n3r.runelite.watchdog.ui.ComparableNumber;
import com.adamk33n3r.runelite.watchdog.ui.panels.PanelUtils;

import net.runelite.client.config.ConfigManager;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class AlertManager {
    @Inject
    private ConfigManager configManager;
    @Inject
    private Gson clientGson;
    @Getter
    private Gson gson;

    @Inject
    private WatchdogConfig watchdogConfig;

    @Inject
    private AlertBackupManager alertBackupManager;

    @Getter
    private final List<Alert> alerts = new CopyOnWriteArrayList<>();

    // TODO: Kinda weird this is in here...
    @Getter
    @Inject
    private WatchdogPanel watchdogPanel;

    @Inject
    private WatchdogPlugin plugin;

    @Inject
    private AlertMigrator alertMigrator;

    @Inject
    @Named("watchdog.pluginVersion")
    private String pluginVersion;

    public static final Type ALERT_TYPE;
    public static final Type ALERT_LIST_TYPE;

    static {
        ALERT_TYPE = new TypeToken<Alert>() {}.getType();
        ALERT_LIST_TYPE = new TypeToken<List<Alert>>() {}.getType();
    }

    @Inject
    private WatchdogGsonFactory gsonFactory;

    @Inject
    private void init() {
        this.gson = this.gsonFactory.create(this.clientGson);
    }

    public void createStarterAlertsIfEmpty() {
        if (!alerts.isEmpty()) {
            return;
        }

        AlertGroup kraken = new AlertGroup("Kraken");
        kraken.setAlerts(List.of(
            new SpawnedAlert("Kraken Dies")
                .setSpawnedDespawned(SpawnedAlert.SpawnedDespawned.DESPAWNED)
                .setSpawnedType(SpawnedAlert.SpawnedType.NPC)
                .setSpawnedName("Kraken")
                .addNotifications(
                    this.plugin.getInjector().getInstance(Overhead.class)
                        .setDisplayTime(3)
                        .setTextColor(Color.CYAN)
                        .setMessage("Sit Krak"),
                    this.plugin.getInjector().getInstance(ScreenMarker.class)
                        .setDisplayTime(8)
                        .setScreenMarkerProperties("Get Fishing Explosive Ready", Color.MAGENTA, Color.BLUE, 2),
                    this.plugin.getInjector().getInstance(RequestFocus.class)
                        .setForceFocus(true)
                ),
            new SpawnedAlert("Whirlpool Appears")
                .setSpawnedDespawned(SpawnedAlert.SpawnedDespawned.DESPAWNED)
                .setSpawnedType(SpawnedAlert.SpawnedType.NPC)
                .setSpawnedName("Whirlpool")
                .setDebounceTime(15000)
                .addNotifications(
                    this.plugin.getInjector().getInstance(ScreenFlash.class)
                        .setColor(ColorUtil.fromHex("#6D0030"))
                        .setFlashMode(FlashMode.SMOOTH_FLASH)
                        .setFlashDuration(1),
                    this.plugin.getInjector().getInstance(Overhead.class)
                        .setDisplayTime(3)
                        .setTextColor(Color.GREEN)
                        .setMessage("Throw Explosive!")
                ),
            new InventoryAlert("Fishing Explosive Used")
                .setInventoryAlertType(InventoryAlert.InventoryAlertType.ITEM_CHANGE)
                .setItemName("Fishing explosive")
                .setItemQuantity(-1)
                .setQuantityComparator(ComparableNumber.Comparator.EQUALS)
                .addNotifications(
                    this.plugin.getInjector().getInstance(Overhead.class)
                        .setDisplayTime(2)
                        .setTextColor(Color.RED)
                        .setMessage("Attack!"),
                    this.plugin.getInjector().getInstance(ScreenFlash.class)
                        .setColor(ColorUtil.fromHex("#46FF00"))
                        .setFlashMode(FlashMode.FLASH)
                        .setFlashDuration(2)
                )
        ));

        AlertGroup chatboxTTS = new AlertGroup("Chatbox TTS");
        chatboxTTS.setEnabled(false);
        chatboxTTS.setAlerts(List.of(
            new PlayerChatAlert("Clan Chat TTS")
                .setPlayerChatType(PlayerChatType.CLAN)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                ),
            new PlayerChatAlert("Friends Chat TTS")
                .setPlayerChatType(PlayerChatType.FRIENDS)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                ),
            new PlayerChatAlert("Guest Clan Chat TTS")
                .setPlayerChatType(PlayerChatType.GUEST_CLAN)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                ),
            new PlayerChatAlert("GIM Chat TTS")
                .setPlayerChatType(PlayerChatType.GIM)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                ),
            new PlayerChatAlert("Private Chat TTS")
                .setPlayerChatType(PlayerChatType.PRIVATE)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                ),
            new PlayerChatAlert("Public Chat TTS - ENABLE AT OWN RISK")
                .setPlayerChatType(PlayerChatType.PUBLIC)
                .setMessage("{*}")
                .addNotification(
                    this.plugin.getInjector().getInstance(TextToSpeech.class)
                        .setGain(5).setRate(2)
                        .setLegacyVoice(Voice.GEORGE)
                        .setSource(TTSSource.LEGACY)
                        .setMessage("$1")
                )
        ));

        ChatAlert cannonReload = new ChatAlert("Reload Cannon");
        cannonReload.setMessage("Your cannon has * cannon balls remaining!");
        cannonReload.setGameMessageType(GameMessageType.ANY);
        cannonReload.addNotifications(
            this.plugin.getInjector().getInstance(ScreenFlash.class)
                .setColor(ColorUtil.fromHex("#46FF00"))
                .setFlashMode(FlashMode.SMOOTH_FLASH)
                .setFlashDuration(2),
            this.plugin.getInjector().getInstance(TextToSpeech.class)
                .setGain(5)
                .setElevenLabsVoiceId("2EiwWnXFnvU5JabPnv8n")
                .setSource(TTSSource.ELEVEN_LABS)
                .setMessage("Reload!")
        );

        ChatAlert readyToHarvest = new ChatAlert("Ready to Harvest");
        readyToHarvest.setMessage("Your {*} is ready to harvest in {*}.");
        readyToHarvest.setDebounceTime(100);
        readyToHarvest.addNotification(
            this.plugin.getInjector().getInstance(TextToSpeech.class)
                .setGain(5).setRate(1)
                .setLegacyVoice(Voice.LUCAS)
                .setSource(TTSSource.LEGACY)
                .setMessage("Your $1 patch in $2 is ready to harvest!")
        );

        AlertGroup starterAlerts = new AlertGroup("Starter Alerts");
        starterAlerts.setAlerts(List.of(
            kraken,
            chatboxTTS,
            cannonReload,
            readyToHarvest
        )).setEnabled(false);

        this.addAlert(starterAlerts, false);
    }

    public Stream<Alert> getAllEnabledAlerts() {
        return this.getAllAlerts().filter(Alert::isEnabled);
    }

    public <T extends Alert> Stream<T> getAllEnabledAlertsOfType(Class<T> type) {
        if (type == AdvancedAlert.class && !this.watchdogConfig.enableAdvancedAlerts()) {
            return Stream.empty();
        }
        return this.getAllEnabledAlerts()
            .filter(type::isInstance)
            .map(type::cast);
    }

    public <T extends Alert> boolean hasEnabledAlertsOfType(Class<T> type) {
        if (type == AdvancedAlert.class && !this.watchdogConfig.enableAdvancedAlerts()) {
            return false;
        }
        return this.getAllEnabledAlerts().anyMatch(type::isInstance);
    }

    public Stream<Alert> getAllAlerts() {
        return this.getAllAlertsFrom(this.alerts.stream(), false);
    }

    public Stream<Alert> getAllAlertsAndGroups() {
        return this.getAllAlertsFrom(this.alerts.stream(), true);
    }

    public <T extends Alert> Stream<T> getAllAlertsOfType(Class<T> type) {
        return this.getAllAlerts()
            .filter(type::isInstance)
            .map(type::cast);
    }

    public Stream<AlertGroup> getAllAlertGroups() {
        return this.getAllAlertsFrom(this.alerts.stream(), true)
            .filter(AlertGroup.class::isInstance)
            .map(AlertGroup.class::cast);
    }

    public Stream<Alert> getAllAlertsFrom(Stream<Alert> alerts, boolean includeGroups) {
        return alerts.flatMap(alert -> {
            if (alert instanceof AlertGroup) {
                Stream<Alert> children = this.getAllAlertsFrom(((AlertGroup) alert).getAlerts().stream(), includeGroups);
                if (includeGroups) {
                    return Stream.concat(Stream.of(alert), children);
                }
                return children;
            }
            return Stream.of(alert);
        });
    }

    public <T extends Alert> T createAlert(Class<T> alertClass) {
        T alert = this.plugin.getInjector().getInstance(alertClass);
        if (alert instanceof AdvancedAlert) {
            ((AdvancedAlert) alert).addWelcomeNote();
        }
        return alert;
    }

    public void addAlert(Alert alert, boolean overrideWithDefaults) {
        this.alerts.add(alert);
        this.setUpAlert(alert, overrideWithDefaults);
        this.saveAlerts();

        SwingUtilities.invokeLater(this.watchdogPanel::rebuild);
    }

    public void removeAlert(Alert alert) {
        this.removeAlert(alert, true);
    }

    public void removeAlert(Alert alert, boolean rebuildPanel) {
        AlertGroup parent = alert.getParent();
        if (parent != null) {
            parent.getAlerts().remove(alert);
        } else {
            this.alerts.remove(alert);
        }
        this.saveAlerts();

        if (rebuildPanel) {
            SwingUtilities.invokeLater(this.watchdogPanel::rebuild);
        }
    }

    public Alert cloneAlert(Alert alert) {
        String json = this.gson.toJson(alert, ALERT_TYPE);
        Alert clonedAlert = this.gson.fromJson(json, ALERT_TYPE);
        this.setUpAlert(clonedAlert, false);
        clonedAlert.setName(clonedAlert.getName() + " Clone");
        return clonedAlert;
    }

    public void moveAlertTo(Alert alert, int pos) {
        AlertGroup parent = alert.getParent();
        if (parent != null) {
            parent.getAlerts().remove(alert);
            parent.getAlerts().add(pos, alert);
        } else {
            this.alerts.remove(alert);
            this.alerts.add(pos, alert);
        }
        this.saveAlerts();
    }

    public void loadAlerts() {
        final String data = this.configManager.getConfiguration(WatchdogConfig.CONFIG_GROUP_NAME, WatchdogConfig.ALERTS);
        final String json = Util.isCompressed(data) ? Util.decompressAlerts(data) : data;
        this.importAlerts(json, this.alerts, false, false, false);
        this.createStarterAlertsIfEmpty();
        this.handleUpgrades();
        if (this.watchdogConfig.disableAllAlertsOnStartup()) {
            this.getAllAlertsFrom(this.alerts.stream(), true).forEach(alert -> alert.setEnabled(false));
        }
    }

    public boolean importAlerts(String json, List<Alert> alerts, boolean append, boolean checkRegex, boolean overrideWithDefaults) throws JsonSyntaxException {
        if (Strings.isNullOrEmpty(json)) {
            return false;
        }

        if (!append) {
            alerts.clear();
        }

        Supplier<Stream<Alert>> alertStream = this.tryImport(json);

        // Validate regex properties
        if (checkRegex && !alertStream.get().allMatch(alert -> {
            if (alert instanceof RegexMatcher) {
                RegexMatcher matcher = (RegexMatcher) alert;
                return PanelUtils.isPatternValid(this.watchdogPanel, matcher.getPattern(), matcher.isRegexEnabled());
            }

            return true;
        })) {
            return false;
        }

        alertStream.get().forEach(alerts::add);

        // Save immediately to save new properties
        this.saveAlerts();

        // Inject dependencies
        this.getAllAlertsFrom(alertStream.get(), false)
            .forEach(alert -> this.setUpAlert(alert, overrideWithDefaults));

        SwingUtilities.invokeLater(() -> {
            this.watchdogPanel.rebuild();
            SwingUtilities.invokeLater(this.watchdogPanel::scrollToBottom);
        });
        return true;
    }

    public void saveAlerts() {
        String json = this.gson.toJson(this.alerts, ALERT_LIST_TYPE);
        this.configManager.setConfiguration(WatchdogConfig.CONFIG_GROUP_NAME, WatchdogConfig.ALERTS, Util.compressAlerts(json));
        this.alertBackupManager.backup(json);
    }

    public String toJSON() {
        return this.gson.toJson(this.alerts, ALERT_LIST_TYPE);
    }

    private Supplier<Stream<Alert>> tryImport(String json) throws JsonSyntaxException {
        // Single
        try {
            Alert importedAlert = this.gson.fromJson(json, ALERT_TYPE);
            return () -> Stream.of(importedAlert).filter(Objects::nonNull);
        } catch (JsonSyntaxException ignored) {
        }

        // Multiple
        List<Alert> importedAlerts = this.gson.fromJson(json, ALERT_LIST_TYPE);
        return () -> importedAlerts.stream().filter(Objects::nonNull);
    }

    private void setUpAlert(Alert alert, boolean overrideWithDefaults) {
        this.plugin.getInjector().injectMembers(alert);
        if (alert instanceof AlertGroup) {
            ((AlertGroup) alert).getAlerts().forEach(subAlert -> this.setUpAlert(subAlert, overrideWithDefaults));
        } else if (alert instanceof AdvancedAlert) {
            AdvancedAlert advancedAlert = (AdvancedAlert) alert;
            advancedAlert.getGraph().getNodes().forEach(node -> {
                if (node instanceof TriggerNode) {
                    this.setUpAlert(((TriggerNode) node).getAlert(), overrideWithDefaults);
                } else if (node instanceof ActionNode) {
                    Notification notification = ((ActionNode) node).getNotification();
                    if (notification instanceof TextToSpeech) {
                        TextToSpeech tts = (TextToSpeech) notification;
                        if (tts.getSource() == TTSSource.ELEVEN_LABS && tts.getElevenLabsVoiceId() != null && !watchdogConfig.elevenLabsAPIKey().isEmpty()) {
                            ElevenLabs.getVoice(this.plugin.getHttpClient(), tts.getElevenLabsVoiceId(), tts::setElevenLabsVoice, log::error);
                        }
                    }
                    this.plugin.getInjector().injectMembers(notification);
                    if (overrideWithDefaults) {
                        notification.setDefaults();
                    }
                    notification.setAlert(alert);
                }
            });
        } else {
            if (alert.getNotifications() == null) {
                return;
            }
            for (INotification notification : alert.getNotifications()) {
                if (notification instanceof TextToSpeech) {
                    TextToSpeech tts = (TextToSpeech) notification;
                    if (tts.getSource() == TTSSource.ELEVEN_LABS && tts.getElevenLabsVoiceId() != null && !watchdogConfig.elevenLabsAPIKey().isEmpty()) {
                        ElevenLabs.getVoice(this.plugin.getHttpClient(), tts.getElevenLabsVoiceId(), tts::setElevenLabsVoice, log::error);
                    }
                }
                this.plugin.getInjector().injectMembers(notification);
                if (overrideWithDefaults) {
                    notification.setDefaults();
                }
                notification.setAlert(alert);
            }
        }
    }

    private void handleUpgrades() {
        Version currentVersion = new Version(this.pluginVersion);
        Version configVersion = new Version(this.configManager.getConfiguration(WatchdogConfig.CONFIG_GROUP_NAME, WatchdogConfig.PLUGIN_VERSION));
        log.debug("currentVersion: {}", currentVersion);
        log.debug("configVersion: {}", configVersion);
        if (currentVersion.compareTo(configVersion) > 0) {
            log.debug("Running data migration from {} to {}", configVersion, currentVersion);
            this.alertMigrator.migrate(this.alerts, configVersion);
            this.configManager.setConfiguration(WatchdogConfig.CONFIG_GROUP_NAME, WatchdogConfig.PLUGIN_VERSION, currentVersion.getVersion());
            this.saveAlerts();
        }
    }
}
