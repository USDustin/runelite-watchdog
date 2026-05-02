package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.runelite.watchdog.alerts.AdvancedAlert;
import com.adamk33n3r.runelite.watchdog.alerts.InventoryAlert;
import com.adamk33n3r.runelite.watchdog.alerts.InventoryAlert.InventoryAlertType;
import com.adamk33n3r.runelite.watchdog.alerts.StatChangedAlert;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventHandlerBannedAreaTest extends AlertTestBase {

    @InjectMocks
    EventHandler eventHandler;

    @Before
    public void setup() {
        doNothing().when(this.watchdogPlugin).processAlert(any(), any(), anyBoolean());
    }

    // region Helpers

    private ChatMessage mockGameMessage(String text) {
        ChatMessage msg = mock(ChatMessage.class);
        when(msg.getName()).thenReturn("OtherPlayer");
        when(msg.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
        when(msg.getMessage()).thenReturn(text);
        return msg;
    }

    private StatChanged statChanged(Skill skill, int level, int boosted, int xp) {
        return new StatChanged(skill, xp, level, boosted);
    }

    private ItemContainerChanged inventoryEvent(Item... items) {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getId()).thenReturn(InventoryID.INV);
        when(container.getItems()).thenReturn(items);
        return new ItemContainerChanged(InventoryID.INV, container);
    }

    private Item item(int id, int quantity) {
        ItemComposition comp = mock(ItemComposition.class);
        when(this.itemManager.getItemComposition(id)).thenReturn(comp);
        return new Item(id, quantity);
    }

    private void stubAlertManagerWith(StatChangedAlert alert) {
        doAnswer(inv -> {
            Class<?> c = inv.getArgument(0);
            if (c == StatChangedAlert.class) return Stream.of(alert);
            return Stream.empty();
        }).when(this.alertManager).getAllEnabledAlertsOfType(any());
    }

    private void stubAlertManagerWith(InventoryAlert alert) {
        doAnswer(inv -> {
            Class<?> c = inv.getArgument(0);
            if (c == InventoryAlert.class) return Stream.of(alert);
            if (c == AdvancedAlert.class) return Stream.empty();
            return Stream.empty();
        }).when(this.alertManager).getAllEnabledAlertsOfType(any());
    }

    // endregion

    // --- Group 1: pure trigger methods get a blanket early return ---

    @Test
    public void onChatMessage_inBannedArea_alertManagerNotConsulted() {
        doReturn(true).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onChatMessage(this.mockGameMessage("some message"));
        verify(this.alertManager, never()).getAllEnabledAlertsOfType(any());
    }

    @Test
    public void onChatMessage_notInBannedArea_alertManagerConsulted() {
        doReturn(false).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onChatMessage(this.mockGameMessage("some message"));
        verify(this.alertManager, atLeastOnce()).getAllEnabledAlertsOfType(any());
    }

    // --- Group 2 (handleStatChanged / handleXPDrop): state table always updates, alerts suppressed ---

    @Test
    public void onStatChanged_inBannedArea_alertManagerNotConsulted() {
        // Event 1: primes table at boosted=97 — null previous, returns early, isInBannedArea() not reached
        this.eventHandler.onStatChanged(this.statChanged(Skill.AGILITY, 99, 97, 1000));

        // Event 2 (banned): has real previous now — alert check skipped before alertManager is consulted
        doReturn(true).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onStatChanged(this.statChanged(Skill.AGILITY, 99, 93, 1000));

        verify(this.alertManager, never()).getAllEnabledAlertsOfType(StatChangedAlert.class);
        verify(this.watchdogPlugin, never()).processAlert(any(), any(), anyBoolean());
    }

    @Test
    public void onStatChanged_bannedAreaUpdatesState_noSpuriousAlertAfterLeaving() {
        // Proves the previousSkillLevelTable is still updated while in a banned area.
        // After event 2 (banned, boosted=93), table records prev=93.
        // Event 3 (not banned, boosted=93) sees prev=93 — no threshold crossing, no alert.
        // If state had NOT been retained, prev would still be 97 → event 3 would fire spuriously.
        StatChangedAlert alert = new StatChangedAlert("test");
        alert.setSkill(Skill.AGILITY);
        this.stubAlertManagerWith(alert);

        // Event 1: primes table at boosted=97 — null previous, isInBannedArea() not reached
        this.eventHandler.onStatChanged(this.statChanged(Skill.AGILITY, 99, 97, 1000));

        // Event 2 (banned): drops to 93; alert suppressed, but table updated to prev=93
        doReturn(true).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onStatChanged(this.statChanged(Skill.AGILITY, 99, 93, 1000));

        // Event 3 (not banned): still 93; prev=93 means no crossing → alert must NOT fire
        doReturn(false).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onStatChanged(this.statChanged(Skill.AGILITY, 99, 93, 1000));

        verify(this.watchdogPlugin, never()).processAlert(any(), any(), anyBoolean());
    }

    // --- Group 2 (onItemContainerChanged): state update always runs, alert firing suppressed ---

    @Test
    public void onItemContainerChanged_inBannedArea_noAlertFired() {
        InventoryAlert alert = new InventoryAlert("Full");
        alert.setInventoryAlertType(InventoryAlertType.FULL);
        this.stubAlertManagerWith(alert);

        Item[] full = new Item[28];
        for (int i = 0; i < 28; i++) full[i] = this.item(i + 1, 1);

        // Event 1 (banned, empty): initializes internal state; no alert on first run regardless
        doReturn(true).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onItemContainerChanged(this.inventoryEvent());

        // Event 2 (banned, full): FULL condition met, but must be suppressed
        this.eventHandler.onItemContainerChanged(this.inventoryEvent(full));

        verify(this.watchdogPlugin, never()).processAlert(any(), any(), anyBoolean());
    }

    @Test
    public void onItemContainerChanged_bannedArea_alertFiresAfterLeaving() {
        InventoryAlert alert = new InventoryAlert("Full");
        alert.setInventoryAlertType(InventoryAlertType.FULL);
        this.stubAlertManagerWith(alert);

        Item[] full = new Item[28];
        for (int i = 0; i < 28; i++) full[i] = this.item(i + 1, 1);

        // Event 1 (banned, empty): initializes state
        doReturn(true).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onItemContainerChanged(this.inventoryEvent());

        // Event 2 (banned, full): suppressed; state tables still updated
        this.eventHandler.onItemContainerChanged(this.inventoryEvent(full));
        verify(this.watchdogPlugin, never()).processAlert(any(), any(), anyBoolean());

        // Event 3 (not banned, full): alert fires (isFireOnChange=false default → fires whenever full)
        doReturn(false).when(this.watchdogPlugin).isInBannedArea();
        this.eventHandler.onItemContainerChanged(this.inventoryEvent(full));
        verify(this.watchdogPlugin).processAlert(eq(alert), any(), anyBoolean());
    }
}
