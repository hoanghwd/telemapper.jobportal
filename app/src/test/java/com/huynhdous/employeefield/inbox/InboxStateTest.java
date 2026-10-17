package com.huynhdous.employeefield.inbox;
import org.junit.Test;
import static org.junit.Assert.*;
public class InboxStateTest {
    @Test public void previewPreservesRoadEstimateAndOnlyIncludesAvailableFields() {
        InboxState.Item item=new InboxState.Item("offer:1","Order","Drop off: Final address\r\nTotal distance: 2 mi (straight-line estimate)\r\nETA: unavailable");
        assertEquals("Drop-off: Final address\nDistance: 2 mi (straight-line estimate)",item.deliveryPreview());
        assertEquals("",new InboxState.Item("offer:2","Service job","Fix a faucet").deliveryPreview());
        assertEquals("Offer: $0.00",new InboxState.Item("offer:3","Order","Offer Amount: $0.00").deliveryPreview());
    }
    @Test public void repeatedOfferProducesOneUnreadMessageAndAlert() {
        InboxState state = new InboxState();
        assertTrue(state.receive(new InboxState.Item("offer-1", "Plumbing", "New request")));
        assertFalse(state.receive(new InboxState.Item("offer-1", "Plumbing", "Retry")));
        assertEquals(1, state.unread(true)); assertEquals(1, state.unread(false));
        state.read("offer-1", true);
        assertEquals(0, state.unread(true)); assertEquals(1, state.unread(false));
        state.read("offer-1", false);
        assertEquals(0, state.unread(false));
        assertFalse(state.receive(new InboxState.Item("offer-1", "Plumbing", "Later retry")));
        assertEquals(0, state.unread(true));
    }
    @Test public void accountsDoNotShareInboxState() {
        InboxState first = new InboxState(), second = new InboxState();
        first.receive(new InboxState.Item("offer-1", "Job", "Details"));
        assertEquals(0, second.unread(true)); assertEquals(1, first.unread(true));
    }
    @Test public void restoredReadStateRemainsRead() {
        InboxState.Item item = new InboxState.Item("offer-1", "Job", "Details");
        item.messageRead = true; item.notificationRead = true;
        InboxState state = new InboxState(); state.receive(item);
        assertEquals(0, state.unread(true)); assertEquals(0, state.unread(false));
        assertTrue(state.receive(new InboxState.Item("offer-2", "Next job", "Details")));
        assertEquals(1, state.unread(true));
    }
}
