package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.runelite.watchdog.notifications.TrayNotification;
import com.google.inject.testing.fieldbinder.Bind;
import net.runelite.client.Notifier;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.inject.Inject;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TrayNotificationTest extends TestBase {

    @Mock
    @Bind
    Notifier notifier;

    @Inject
    TrayNotification trayNotification;

    @Test
    public void testFireSendsTrayOnlyNotification() {
        this.trayNotification.setMessage("Test tray message");
        this.trayNotification.fireForced(new String[0]);

        ArgumentCaptor<net.runelite.client.config.Notification> notifCaptor =
            ArgumentCaptor.forClass(net.runelite.client.config.Notification.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.notifier).notify(notifCaptor.capture(), messageCaptor.capture());

        net.runelite.client.config.Notification notif = notifCaptor.getValue();
        assertTrue(notif.isEnabled());
        assertTrue(notif.isInitialized());
        assertTrue(notif.isOverride());
        assertTrue(notif.isTray());
        assertTrue(notif.isSendWhenFocused());
        assertFalse(notif.isGameMessage());
        assertEquals(RequestFocusType.OFF, notif.getRequestFocus());
        assertEquals(NotificationSound.OFF, notif.getSound());
        assertEquals(FlashNotification.DISABLED, notif.getFlash());
        assertEquals("Test tray message", messageCaptor.getValue());
    }

    @Test
    public void testFireProcessesTriggerValues() {
        this.trayNotification.setMessage("Hello $1");
        this.trayNotification.fireForced(new String[]{"world"});

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.notifier).notify(
            any(net.runelite.client.config.Notification.class),
            messageCaptor.capture()
        );
        assertEquals("Hello world", messageCaptor.getValue());
    }
}
