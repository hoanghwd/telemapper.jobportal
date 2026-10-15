package com.huynhdous.employeefield.dispatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.location.TrackingService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class DispatchPollingTest {
    @Test public void readyPollsAtServerIntervalAndUnavailableStopsRequests() throws Exception {
        String token="poll-ready-fixture";TrackingService.activeToken=token;
        AtomicLong clock=new AtomicLong(1000000);List<String> sent=new ArrayList<>();
        DispatchPolling poll=new DispatchPolling(RuntimeEnvironment.getApplication(),token,91,(action,body)->{
            sent.add(body.getString("availability"));
            return new JSONObject().put("poll_interval_seconds",15).put("availability",body.getString("availability"));
        },clock::get);
        DispatchPolling.ready(token,true);poll.tick();assertEquals(1,sent.size());
        clock.addAndGet(14000);poll.tick();assertEquals(1,sent.size());
        clock.addAndGet(1000);poll.tick();assertEquals(2,sent.size());
        DispatchPolling.ready(token,false);poll.tick();assertEquals("unavailable",sent.get(2));
        clock.addAndGet(60000);poll.tick();assertEquals(3,sent.size());
        poll.stop();TrackingService.activeToken="";
    }
    @Test public void failedUnavailableRetriesWithBackoff() throws Exception {
        String token="poll-retry-fixture";TrackingService.activeToken=token;
        AtomicLong clock=new AtomicLong(1000000);List<String> sent=new ArrayList<>();
        DispatchPolling poll=new DispatchPolling(RuntimeEnvironment.getApplication(),token,92,(action,body)->{
            sent.add(body.getString("availability"));
            if(sent.size()==2)throw new Api.ApiError(503,"Offline");
            return new JSONObject().put("poll_interval_seconds",15);
        },clock::get);
        DispatchPolling.ready(token,true);poll.tick();
        DispatchPolling.ready(token,false);poll.tick();assertEquals(2,sent.size());
        poll.tick();assertEquals(2,sent.size());
        clock.addAndGet(15000);poll.tick();assertEquals(3,sent.size());
        assertEquals("unavailable",sent.get(2));poll.stop();TrackingService.activeToken="";
    }
    @Test public void revokedOrChangedSessionDoesNotPoll() throws Exception {
        String token="poll-ended-fixture";TrackingService.activeToken="";
        List<String> sent=new ArrayList<>();
        DispatchPolling poll=new DispatchPolling(RuntimeEnvironment.getApplication(),token,93,(action,body)->{
            sent.add(action);return new JSONObject();
        },()->1000000L);
        DispatchPolling.ready(token,true);poll.tick();assertTrue(sent.isEmpty());
        DispatchPolling.ready(token,false);
    }
}
