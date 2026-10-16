package com.huynhdous.employeefield.jobs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JobsMapTest {
    @Test
    public void thePageCarriesTheJobsTheToolbarAndTheScripts() throws Exception {
        JSONObject data = new JSONObject().put("can_take", true).put("jobs", new org.json.JSONArray().put(new JSONObject().put("request_id", 7).put("title", "Bakery delivery sweethouse-TP1")));
        String html = JobsMap.buildHtml(data);
        assertTrue(html.contains("var jobsData={"));
        assertTrue(html.contains("\"request_id\":7"));
        assertTrue(html.contains("<script src=\"jobs-map.js\"></script>"));
        assertTrue(html.contains("<script src=\"leaflet.js\"></script>"));
        assertTrue(html.contains("id=\"where\"") && html.contains("id=\"refresh\"") && html.contains("id=\"fit\""));
        assertTrue(html.contains("var APP_CONFIG="));
    }

    @Test
    public void textFromTheServerCannotCloseTheScriptBlock() throws Exception {
        JSONObject data = new JSONObject().put("message", "</script><script>alert(1)</script>");
        String html = JobsMap.buildHtml(data);
        assertFalse(html.contains("</script><script>alert(1)"));
        assertTrue(html.contains("\\u003c") && html.indexOf("</script>", html.indexOf("var jobsData=")) == html.lastIndexOf("</script>", html.indexOf("jobs-map.js")));
    }
}
