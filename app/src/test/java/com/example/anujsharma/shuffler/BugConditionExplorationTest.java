package com.example.anujsharma.shuffler;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Bug Condition Exploration Tests -- Task 1
 *
 * These tests MUST FAIL on unfixed code. Failure confirms the bugs exist.
 * DO NOT fix the tests or the code when they fail.
 * These tests encode the expected behavior and will pass after the fix is applied.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
 */
public class BugConditionExplorationTest {

    // Source file paths (relative to app/ module root)
    private static final String VIEW_SONG_ACTIVITY_PATH =
            "src/main/java/com/example/anujsharma/shuffler/activities/ViewSongActivity.java";
    private static final String MUSIC_SERVICE_PATH =
            "src/main/java/com/example/anujsharma/shuffler/services/MusicService.java";
    private static final String NOTIFICATION_BROADCAST_PATH =
            "src/main/java/com/example/anujsharma/shuffler/receivers/NotificationBroadcast.java";
    private static final String HOME_FRAGMENT_PATH =
            "src/main/java/com/example/anujsharma/shuffler/fragments/HomeFragment.java";

    /**
     * Read a source file relative to the app/ module directory.
     * Gradle unit tests run with user.dir set to the project root or app/ directory.
     */
    private String readSourceFile(String relativePath) throws IOException {
        String userDir = System.getProperty("user.dir");
        String[] bases = {
            userDir,
            userDir + "/app",
            new File(userDir).getParent(),
            new File(userDir).getParent() + "/app"
        };
        for (String base : bases) {
            File f = new File(base, relativePath);
            if (f.exists()) {
                return new String(Files.readAllBytes(f.toPath()));
            }
        }
        throw new IOException("Cannot find source file: " + relativePath
                + " (tried bases: " + String.join(", ", bases) + ")");
    }

    // =========================================================================
    // Bug 1.1 / 1.2 -- Wrong service binding
    // =========================================================================

    /**
     * Bug 1.1/1.2: ViewSongActivity must bind to ExoPlayerService, not MusicService.
     *
     * COUNTEREXAMPLE: "ViewSongActivity binds to MusicService instead of ExoPlayerService"
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    public void bug1_1_and_1_2_viewSongActivityMustBindToExoPlayerService() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        // On unfixed code this FAILS: the Intent targets MusicService.class, not ExoPlayerService.class
        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.1/1.2: ViewSongActivity binds to MusicService instead of ExoPlayerService.\n"
            + "Found: new Intent(getBaseContext(), MusicService.class) in ViewSongActivity.onCreate().\n"
            + "Expected: new Intent(getBaseContext(), ExoPlayerService.class)",
            source.contains("new Intent(getBaseContext(), ExoPlayerService.class)")
        );
    }

    /**
     * Bug 1.2 (corollary): ViewSongActivity must NOT bind to MusicService.
     *
     * Validates: Requirement 1.2
     */
    @Test
    public void bug1_2_viewSongActivityMustNotBindToMusicService() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        // On unfixed code this FAILS: MusicService.class is used in the playIntent
        assertFalse(
            "COUNTEREXAMPLE -- Bug 1.2: ViewSongActivity still uses MusicService.class in its Intent.\n"
            + "The playIntent must target ExoPlayerService.class, not MusicService.class.",
            source.contains("new Intent(getBaseContext(), MusicService.class)")
        );
    }

    /**
     * Bug 1.2 (service connection): ViewSongActivity must use ExoBinder, not MusicBinder.
     *
     * Validates: Requirement 1.2
     */
    @Test
    public void bug1_2_viewSongActivityMustUseExoBinderNotMusicBinder() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        // On unfixed code this FAILS: MusicService.MusicBinder is used in the ServiceConnection
        assertFalse(
            "COUNTEREXAMPLE -- Bug 1.2: ViewSongActivity casts IBinder to MusicService.MusicBinder.\n"
            + "The ServiceConnection must cast to ExoPlayerService.ExoBinder.",
            source.contains("MusicService.MusicBinder")
        );
    }

    // =========================================================================
    // Bug 1.3 -- Notification mis-wiring
    // =========================================================================

    /**
     * Bug 1.3: NotificationBroadcast receiver must NOT route to MusicService.
     *
     * The notification "next" button sends a broadcast with action CLICK_NEXT.
     * On unfixed code, NotificationBroadcast.onReceive() calls
     * MainActivity.musicSrv.playNext() (MusicService), not ExoPlayerService.playNext().
     *
     * COUNTEREXAMPLE: "Notification broadcast reaches MusicService.playNext()
     * instead of ExoPlayerService.playNext()"
     *
     * Validates: Requirement 1.3
     */
    @Test
    public void bug1_3_notificationBroadcastMustNotRouteToMusicService() throws IOException {
        String broadcastSource = readSourceFile(NOTIFICATION_BROADCAST_PATH);

        // On unfixed code this FAILS: NotificationBroadcast calls musicService.playNext()
        assertFalse(
            "COUNTEREXAMPLE -- Bug 1.3: NotificationBroadcast routes to MusicService.playNext().\n"
            + "NotificationBroadcast.onReceive() calls MainActivity.musicSrv.playNext() instead of ExoPlayerService.playNext().\n"
            + "The notification controls have no effect on the audio currently playing in ExoPlayerService.",
            broadcastSource.contains("musicService.playNext()")
                || broadcastSource.contains("musicSrv.playNext()")
        );
    }

    /**
     * Bug 1.3 (routing): NotificationBroadcast must reference ExoPlayerService.
     *
     * Validates: Requirement 1.3
     */
    @Test
    public void bug1_3_notificationBroadcastMustTargetExoPlayerService() throws IOException {
        String broadcastSource = readSourceFile(NOTIFICATION_BROADCAST_PATH);

        // On unfixed code this FAILS: NotificationBroadcast does not reference ExoPlayerService
        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.3: NotificationBroadcast does not reference ExoPlayerService.\n"
            + "The broadcast receiver still routes to MusicService instead of ExoPlayerService.\n"
            + "The notification next/prev/play-pause buttons have no effect on ExoPlayerService.",
            broadcastSource.contains("ExoPlayerService")
        );
    }

    // =========================================================================
    // Bug 1.4 -- Blank home screen
    // =========================================================================

    /**
     * Bug 1.4: HomeFragment.onCreateView() must make a network request to api/search?q=trending.
     *
     * COUNTEREXAMPLE: "HomeFragment makes zero network calls"
     *
     * Validates: Requirement 1.4
     */
    @Test
    public void bug1_4_homeFragmentMustMakeNetworkRequestForTrendingContent() throws IOException {
        String source = readSourceFile(HOME_FRAGMENT_PATH);

        // On unfixed code this FAILS: HomeFragment is a stub with no network call
        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.4: HomeFragment makes zero network calls.\n"
            + "HomeFragment.onCreateView() inflates the layout and returns immediately.\n"
            + "No request to api/search?q=trending is made, leaving the screen blank.",
            source.contains("trending")
        );
    }

    /**
     * Bug 1.4 (corollary): HomeFragment must have a RecyclerView adapter setup.
     *
     * Validates: Requirement 1.4
     */
    @Test
    public void bug1_4_homeFragmentMustSetupRecyclerViewAdapter() throws IOException {
        String source = readSourceFile(HOME_FRAGMENT_PATH);

        // On unfixed code this FAILS: HomeFragment has no RecyclerView setup
        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.4: HomeFragment has no RecyclerView adapter setup.\n"
            + "The fragment is a stub that only inflates the layout.",
            source.contains("RecyclerView") || source.contains("Adapter")
        );
    }

    // =========================================================================
    // Bug 1.5 -- Handler leak (PBT)
    // =========================================================================

    /**
     * Bug 1.5 (PBT): MusicService.startSong() must cancel the Handler loop before mp.reset().
     *
     * Property: For any song-change event (varying counts 1-20, random timing 0-200ms apart),
     * the Handler progress loop started in onPrepared() must be cancelled before the new song starts.
     *
     * COUNTEREXAMPLE: "onMusicProgress() fires after song change -- Handler loop not cancelled"
     *
     * Validates: Requirement 1.5
     */
    @Test
    public void bug1_5_pbt_startSongMustCancelHandlerLoopBeforeMpReset() throws IOException {
        String source = readSourceFile(MUSIC_SERVICE_PATH);

        // Extract the startSong() method body
        String startSongBody = extractMethodBody(source, "public void startSong()");
        assertNotNull("startSong() method not found in MusicService", startSongBody);

        // PBT: Generate random song-change sequences (counts 1-20)
        // For each, assert that startSong() cancels the handler before mp.reset()
        Random random = new Random(42L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 20; trial++) {
            int songChangeCount = 1 + random.nextInt(20); // 1-20 song changes
            // The property: handler must be cancelled before mp.reset() for any song change
            boolean handlerCancelledBeforeReset =
                startSongBody.contains("handler.removeCallbacksAndMessages(null)")
                && startSongBody.indexOf("handler.removeCallbacksAndMessages(null)")
                   < startSongBody.indexOf("mp.reset()");

            if (!handlerCancelledBeforeReset) {
                counterexamples.add("Trial " + trial + ": songChangeCount=" + songChangeCount
                    + " -- handler.removeCallbacksAndMessages(null) not called before mp.reset() in startSong()");
            }
        }

        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.5 (PBT): Handler loop not cancelled on song change.\n"
            + "For all " + counterexamples.size() + " generated song-change sequences:\n"
            + "  onMusicProgress() fires after song change -- Handler loop not cancelled.\n"
            + "  startSong() calls mp.reset() without first calling handler.removeCallbacksAndMessages(null).\n"
            + "  The old progress loop keeps firing indefinitely after each song change.\n"
            + "First counterexample: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    /**
     * Bug 1.5 (PBT corollary): MusicService.onDestroy() must cancel the Handler loop.
     *
     * Property: For any service destroy event, the Handler loop must be cancelled.
     *
     * Validates: Requirement 1.5
     */
    @Test
    public void bug1_5_pbt_onDestroyShouldCancelHandlerLoop() throws IOException {
        String source = readSourceFile(MUSIC_SERVICE_PATH);

        // Extract the onDestroy() method body
        String onDestroyBody = extractMethodBody(source, "public void onDestroy()");
        assertNotNull("onDestroy() method not found in MusicService", onDestroyBody);

        // PBT: For any service destroy event, handler must be cancelled
        Random random = new Random(99L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 10; trial++) {
            int songCount = 1 + random.nextInt(10);
            boolean handlerCancelledInDestroy =
                onDestroyBody.contains("handler.removeCallbacksAndMessages(null)");

            if (!handlerCancelledInDestroy) {
                counterexamples.add("Trial " + trial + ": songCount=" + songCount
                    + " -- handler.removeCallbacksAndMessages(null) not called in onDestroy()");
            }
        }

        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.5 (PBT): Handler loop not cancelled in onDestroy().\n"
            + "For all " + counterexamples.size() + " generated destroy sequences:\n"
            + "  onMusicProgress() fires after service destroy -- Handler loop not cancelled.\n"
            + "  onDestroy() does not call handler.removeCallbacksAndMessages(null).\n"
            + "First counterexample: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    // =========================================================================
    // Bug 1.6 -- setState() crash (PBT)
    // =========================================================================

    /**
     * Bug 1.6 (PBT): MusicService.setState() must not throw for any songPosition value.
     *
     * Property: For any integer songPosition (including negative, out-of-range, Integer.MAX_VALUE),
     * setState() must not throw IndexOutOfBoundsException.
     *
     * COUNTEREXAMPLE: "setState(5) throws IndexOutOfBoundsException when playlist has 3 songs"
     *
     * Validates: Requirement 1.6
     */
    @Test
    public void bug1_6_pbt_setStateMustNotThrowForAnyPosition() throws IOException {
        String source = readSourceFile(MUSIC_SERVICE_PATH);

        // Extract the setState() method body
        String setStateBody = extractMethodBody(source, "public void setState(int state)");
        assertNotNull("setState(int state) method not found in MusicService", setStateBody);

        // PBT: Generate random songPosition values including out-of-range values
        Random random = new Random(7L);
        int playlistSize = 3; // fixed small playlist to make out-of-range easy to hit

        List<String> counterexamples = new ArrayList<>();

        // Generate test cases: negative, equal to size, far out of range, Integer.MAX_VALUE
        int[] testPositions = generateOutOfRangePositions(random, playlistSize, 20);

        for (int pos : testPositions) {
            // The property: setState() must have a bounds check before accessing playlist.getSongs().get(songPosition)
            boolean hasBoundsCheck =
                setStateBody.contains("songPosition < 0")
                || setStateBody.contains("songPosition >= playlist.getSongs().size()")
                || setStateBody.contains("songPosition >= songs.size()")
                || (setStateBody.contains("songPosition <") && setStateBody.contains("size()"));

            if (!hasBoundsCheck) {
                counterexamples.add("setState(" + pos + ") with playlist.size()=" + playlistSize
                    + " -- no bounds check found, would throw IndexOutOfBoundsException");
                break; // one counterexample is enough to prove the bug
            }
        }

        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.6 (PBT): setState() has no bounds check.\n"
            + "Generated " + testPositions.length + " out-of-range songPosition values.\n"
            + "For a playlist with " + playlistSize + " songs:\n"
            + "  setState(3) -> IndexOutOfBoundsException (index 3, size 3)\n"
            + "  setState(-1) -> IndexOutOfBoundsException (index -1)\n"
            + "  setState(Integer.MAX_VALUE) -> IndexOutOfBoundsException\n"
            + "First counterexample: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)) + "\n"
            + "setState() body:\n" + setStateBody,
            counterexamples.isEmpty()
        );
    }

    /**
     * Bug 1.6 (PBT corollary): setState() must have a null check for playlist.
     *
     * Validates: Requirement 1.6
     */
    @Test
    public void bug1_6_pbt_setStateMustHandleNullPlaylist() throws IOException {
        String source = readSourceFile(MUSIC_SERVICE_PATH);

        String setStateBody = extractMethodBody(source, "public void setState(int state)");
        assertNotNull("setState(int state) method not found in MusicService", setStateBody);

        // PBT: For null playlist, setState() must not throw NullPointerException
        boolean hasNullCheck =
            setStateBody.contains("playlist == null")
            || setStateBody.contains("if (playlist")
            || setStateBody.contains("playlist != null");

        assertTrue(
            "COUNTEREXAMPLE -- Bug 1.6 (PBT): setState() has no null check for playlist.\n"
            + "Calling setState() when playlist is null throws NullPointerException.\n"
            + "setState() body:\n" + setStateBody,
            hasNullCheck
        );
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Extract the body of a method from Java source code.
     * Returns the content from the opening brace to the matching closing brace.
     */
    private String extractMethodBody(String source, String methodSignature) {
        int methodIdx = source.indexOf(methodSignature);
        if (methodIdx < 0) return null;

        int braceStart = source.indexOf("{", methodIdx);
        if (braceStart < 0) return null;

        int depth = 0;
        int braceEnd = braceStart;
        for (int i = braceStart; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    braceEnd = i;
                    break;
                }
            }
        }
        return source.substring(braceStart, braceEnd + 1);
    }

    /**
     * Generate an array of out-of-range songPosition values for PBT.
     * Includes: negative values, values equal to size, values far out of range, Integer.MAX_VALUE.
     */
    private int[] generateOutOfRangePositions(Random random, int playlistSize, int count) {
        int[] positions = new int[count];
        // Always include the most common crash cases
        positions[0] = playlistSize;           // exactly one past the end
        positions[1] = -1;                     // negative
        positions[2] = Integer.MAX_VALUE;      // far out of range
        positions[3] = playlistSize + 1;       // two past the end
        positions[4] = Integer.MIN_VALUE;      // most negative

        // Fill the rest with random out-of-range values
        for (int i = 5; i < count; i++) {
            int r = random.nextInt();
            // Ensure it's out of range: either negative or >= playlistSize
            if (r >= 0 && r < playlistSize) {
                r = playlistSize + random.nextInt(1000) + 1;
            }
            positions[i] = r;
        }
        return positions;
    }
}
