package com.example.anujsharma.shuffler;

import org.junit.Test;
import com.example.anujsharma.shuffler.utilities.YouTubeInAppClient;

public class YouTubeInAppClientTest {
    @Test
    public void testResolve() throws Exception {
        YouTubeInAppClient client = new YouTubeInAppClient();
        String url = client.resolveBestAudioStreamUrl("kJQP7kiw5Fk");
        System.out.println("Resolved URL: " + url);
    }
}
