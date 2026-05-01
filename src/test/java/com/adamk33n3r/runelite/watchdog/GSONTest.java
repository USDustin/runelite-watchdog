package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.runelite.watchdog.notifications.TextToSpeech;
import com.adamk33n3r.runelite.watchdog.notifications.tts.TTSSource;
import com.adamk33n3r.runelite.watchdog.serialization.WatchdogGsonFactory;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GSONTest {
    @Test
    public void json_missing_property_with_initializer_test() throws Exception {
        Gson gson = new WatchdogGsonFactory().create(new Gson());
        TextToSpeech tts = gson.fromJson("{\"type\":\"TextToSpeech\", \"message\":\"this is a test\"}", TextToSpeech.class);

        assertEquals(TTSSource.ELEVEN_LABS, tts.getSource());
        assertEquals(5, tts.getGain());
    }
}
