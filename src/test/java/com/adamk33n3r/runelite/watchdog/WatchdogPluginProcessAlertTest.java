package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.runelite.watchdog.alerts.Alert;
import com.adamk33n3r.runelite.watchdog.alerts.AlertGroup;
import com.adamk33n3r.runelite.watchdog.alerts.AlertMode;
import com.adamk33n3r.runelite.watchdog.notifications.Notification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class WatchdogPluginProcessAlertTest {
    // Long enough that a sleeping processor cannot possibly fire before we interrupt it
    private static final int LONG_DELAY_MS = 5_000;

    private WatchdogPlugin plugin;
    private Alert alert;
    private Alert alert2;
    private Notification slowNotification;
    private Notification slowNotification2;
    private Notification fastNotification;

    @Before
    public void setUp() {
        plugin = new WatchdogPlugin();
        alert = mock(Alert.class);
        alert2 = mock(Alert.class);
        slowNotification = mock(Notification.class);
        slowNotification2 = mock(Notification.class);
        fastNotification = mock(Notification.class);

        when(slowNotification.shouldFire()).thenReturn(true);
        when(slowNotification.getDelayMilliseconds()).thenReturn(LONG_DELAY_MS);

        when(slowNotification2.shouldFire()).thenReturn(true);
        when(slowNotification2.getDelayMilliseconds()).thenReturn(LONG_DELAY_MS);

        when(fastNotification.shouldFire()).thenReturn(true);
        when(fastNotification.getDelayMilliseconds()).thenReturn(0);
    }

    @After
    public void tearDown() {
        plugin.stopAllAlerts();
    }

    @Test
    public void processAlert_restartMode_interruptsExistingProcessorBeforeItFires() throws InterruptedException {
        CountDownLatch fastFired = new CountDownLatch(1);
        doAnswer(inv -> { fastFired.countDown(); return null; }).when(fastNotification).fire(any());

        when(alert.getAlertMode()).thenReturn(AlertMode.RESTART);
        when(alert.isRandomNotifications()).thenReturn(false);
        // First processAlert gets the slow notification, second gets the fast one
        when(alert.getNotifications())
            .thenReturn(List.of(slowNotification))
            .thenReturn(List.of(fastNotification));

        plugin.processAlert(alert, new String[0], false);
        Thread.sleep(50); // let first processor reach Thread.sleep(LONG_DELAY_MS)

        plugin.processAlert(alert, new String[0], false); // RESTART: interrupts first, starts second

        assertTrue("Second processor should fire its fast notification", fastFired.await(2, TimeUnit.SECONDS));
        verify(slowNotification, never()).fire(any()); // first was cut off before firing
    }

    @Test
    public void stopAlertProcessors_interruptsOnlyProcessorsForMatchingAlert() throws InterruptedException {
        CountDownLatch fastFired = new CountDownLatch(1);
        doAnswer(inv -> { fastFired.countDown(); return null; }).when(fastNotification).fire(any());

        when(alert.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(alert.getNotifications()).thenReturn(List.of(slowNotification));
        when(alert.isRandomNotifications()).thenReturn(false);

        when(alert2.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(alert2.getNotifications()).thenReturn(List.of(fastNotification));
        when(alert2.isRandomNotifications()).thenReturn(false);

        plugin.processAlert(alert, new String[0], false);   // will sleep LONG_DELAY_MS
        plugin.processAlert(alert2, new String[0], false);  // fires immediately

        assertTrue("alert2 should fire before we stop alert", fastFired.await(2, TimeUnit.SECONDS));
        Thread.sleep(50); // ensure alert's processor is sleeping

        plugin.stopAlertProcessors(alert);

        verify(slowNotification, never()).fire(any());         // alert was interrupted
        verify(fastNotification, times(1)).fire(any());        // alert2 was not stopped
    }

    @Test
    public void stopAllAlerts_interruptsAllActiveProcessors() throws InterruptedException {
        when(alert.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(alert.getNotifications()).thenReturn(List.of(slowNotification));
        when(alert.isRandomNotifications()).thenReturn(false);

        when(alert2.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(alert2.getNotifications()).thenReturn(List.of(slowNotification2));
        when(alert2.isRandomNotifications()).thenReturn(false);

        plugin.processAlert(alert, new String[0], false);
        plugin.processAlert(alert2, new String[0], false);
        Thread.sleep(50); // both processors sleeping

        plugin.stopAllAlerts();

        verify(slowNotification, never()).fire(any());
        verify(slowNotification2, never()).fire(any());
    }

    @Test
    public void stopAlertProcessors_alertGroup_recursivelyStopsAllChildAlerts() throws InterruptedException {
        Alert child1 = mock(Alert.class);
        Alert child2 = mock(Alert.class);
        Notification childNotification1 = mock(Notification.class);
        Notification childNotification2 = mock(Notification.class);

        when(childNotification1.shouldFire()).thenReturn(true);
        when(childNotification1.getDelayMilliseconds()).thenReturn(LONG_DELAY_MS);
        when(childNotification2.shouldFire()).thenReturn(true);
        when(childNotification2.getDelayMilliseconds()).thenReturn(LONG_DELAY_MS);

        when(child1.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(child1.getNotifications()).thenReturn(List.of(childNotification1));
        when(child1.isRandomNotifications()).thenReturn(false);

        when(child2.getAlertMode()).thenReturn(AlertMode.MULTI);
        when(child2.getNotifications()).thenReturn(List.of(childNotification2));
        when(child2.isRandomNotifications()).thenReturn(false);

        AlertGroup group = new AlertGroup();
        group.getAlerts().add(child1);
        group.getAlerts().add(child2);

        plugin.processAlert(child1, new String[0], false);
        plugin.processAlert(child2, new String[0], false);
        Thread.sleep(50); // both sleeping

        plugin.stopAlertProcessors(group); // should recurse into child1 and child2

        verify(childNotification1, never()).fire(any());
        verify(childNotification2, never()).fire(any());
    }
}
