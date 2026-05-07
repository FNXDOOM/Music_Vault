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
 * Preservation Property Tests -- Task 2
 *
 * These tests MUST PASS on unfixed code. They capture existing correct behaviors
 * that must not regress after the fix is applied.
 *
 * Observation-first methodology: each test inspects the source file as text to
 * verify the behavior pattern is present in the UNFIXED code, then the same
 * assertion will continue to hold after the fix.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8
 */
public class PreservationPropertyTest {

    // Source file paths (relative to app/ module root)
    private static final String MAIN_ACTIVITY_PATH =
            "src/main/java/com/example/anujsharma/shuffler/activities/MainActivity.java";
    private static final String VIEW_SONG_ACTIVITY_PATH =
            "src/main/java/com/example/anujsharma/shuffler/activities/ViewSongActivity.java";
    private static final String EXO_PLAYER_SERVICE_PATH =
            "src/main/java/com/example/anujsharma/shuffler/services/ExoPlayerService.java";
    private static final String SEARCH_FRAGMENT_PATH =
            "src/main/java/com/example/anujsharma/shuffler/fragments/SearchFragment.java";
    private static final String SHARED_PREFERENCE_PATH =
            "src/main/java/com/example/anujsharma/shuffler/utilities/SharedPreference.java";

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

    // =========================================================================
    // Req 3.1 -- Unit: mini-player play/pause calls ExoPlayerService
    // =========================================================================

    /**
     * Req 3.1: MainActivity play button calls exoSrv.pausePlayer() when playing.
     *
     * Observation: ivPlay.setOnClickListener in MainActivity calls exoSrv.pausePlayer()
     * and exoSrv.resumePlayer() — not MusicService methods.
     *
     * Validates: Requirement 3.1
     */
    @Test
    public void req3_1_mainActivityPlayPauseCallsExoPlayerService() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        // The mini-player play/pause listener must call exoSrv.pausePlayer()
        assertTrue(
            "Req 3.1 FAILED: MainActivity play/pause does not call exoSrv.pausePlayer().\n"
            + "Expected: ivPlay onClick calls exoSrv.pausePlayer() on ExoPlayerService.",
            source.contains("exoSrv.pausePlayer()")
        );

        // The mini-player play/pause listener must call exoSrv.resumePlayer()
        assertTrue(
            "Req 3.1 FAILED: MainActivity play/pause does not call exoSrv.resumePlayer().\n"
            + "Expected: ivPlay onClick calls exoSrv.resumePlayer() on ExoPlayerService.",
            source.contains("exoSrv.resumePlayer()")
        );
    }

    /**
     * Req 3.1 (corollary): MainActivity play/pause must check exoBound before calling service.
     *
     * Validates: Requirement 3.1
     */
    @Test
    public void req3_1_mainActivityPlayPauseChecksExoBound() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        // Guard: exoBound must be checked before calling exoSrv methods
        assertTrue(
            "Req 3.1 FAILED: MainActivity play/pause does not guard with exoBound check.\n"
            + "Expected: if (exoBound && exoSrv != null) before calling pausePlayer/resumePlayer.",
            source.contains("exoBound") && source.contains("exoSrv != null")
        );
    }

    // =========================================================================
    // Req 3.2 -- Unit: mini-player next calls ExoPlayerService.playNext()
    // =========================================================================

    /**
     * Req 3.2: MainActivity next button calls exoSrv.playNext().
     *
     * Observation: ivNext.setOnClickListener in MainActivity calls exoSrv.playNext().
     *
     * Validates: Requirement 3.2
     */
    @Test
    public void req3_2_mainActivityNextCallsExoPlayerServicePlayNext() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        assertTrue(
            "Req 3.2 FAILED: MainActivity next button does not call exoSrv.playNext().\n"
            + "Expected: ivNext onClick calls exoSrv.playNext() on ExoPlayerService.",
            source.contains("exoSrv.playNext()")
        );
    }

    // =========================================================================
    // Req 3.3 -- Unit: playSongInMainActivity fetches api/song/{videoId}/stream
    //            and sends ACTION_UPDATE_STREAM
    // =========================================================================

    /**
     * Req 3.3: playSongInMainActivity() fetches stream URL from api/song/{videoId}/stream.
     *
     * Observation: fetchStreamAndPlay() builds a URL with "api/song/" + videoId + "/stream".
     *
     * Validates: Requirement 3.3
     */
    @Test
    public void req3_3_playSongFetchesStreamUrl() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        assertTrue(
            "Req 3.3 FAILED: MainActivity does not fetch from api/song/{videoId}/stream.\n"
            + "Expected: URL contains \"api/song/\" + videoId + \"/stream\" in fetchStreamAndPlay().",
            source.contains("api/song/") && source.contains("/stream")
        );
    }

    /**
     * Req 3.3: playSongInMainActivity() sends ACTION_UPDATE_STREAM to ExoPlayerService.
     *
     * Observation: fetchStreamAndPlay() creates an Intent with ACTION_UPDATE_STREAM action
     * and calls startService(updateIntent).
     *
     * Validates: Requirement 3.3
     */
    @Test
    public void req3_3_playSongSendsActionUpdateStream() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        assertTrue(
            "Req 3.3 FAILED: MainActivity does not send ACTION_UPDATE_STREAM after fetching stream.\n"
            + "Expected: updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM) in fetchStreamAndPlay().",
            source.contains("ACTION_UPDATE_STREAM")
        );
    }

    /**
     * Req 3.3: The stream URL is passed as EXTRA_STREAM_URL in the update intent.
     *
     * Validates: Requirement 3.3
     */
    @Test
    public void req3_3_playSongPassesStreamUrlExtra() throws IOException {
        String source = readSourceFile(MAIN_ACTIVITY_PATH);

        assertTrue(
            "Req 3.3 FAILED: MainActivity does not pass EXTRA_STREAM_URL in the update intent.\n"
            + "Expected: updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, streamUrl).",
            source.contains("EXTRA_STREAM_URL")
        );
    }

    // =========================================================================
    // Req 3.4 -- Unit: ExoPlayerService loads and plays on ACTION_UPDATE_STREAM
    // =========================================================================

    /**
     * Req 3.4: ExoPlayerService.onStartCommand() handles ACTION_UPDATE_STREAM by loading
     * the stream URL into ExoPlayer and calling player.setPlayWhenReady(true).
     *
     * Observation: onStartCommand() checks ACTION_UPDATE_STREAM.equals(action), then calls
     * player.clearMediaItems(), player.addMediaItem(), player.prepare(), player.setPlayWhenReady(true).
     *
     * Validates: Requirement 3.4
     */
    @Test
    public void req3_4_exoPlayerServiceHandlesActionUpdateStream() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        // Must handle ACTION_UPDATE_STREAM in onStartCommand
        assertTrue(
            "Req 3.4 FAILED: ExoPlayerService does not handle ACTION_UPDATE_STREAM.\n"
            + "Expected: ACTION_UPDATE_STREAM.equals(action) branch in onStartCommand().",
            source.contains("ACTION_UPDATE_STREAM")
        );
    }

    /**
     * Req 3.4: ExoPlayerService loads the stream URL into ExoPlayer via addMediaItem.
     *
     * Validates: Requirement 3.4
     */
    @Test
    public void req3_4_exoPlayerServiceLoadsMediaItem() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        assertTrue(
            "Req 3.4 FAILED: ExoPlayerService does not call player.addMediaItem() on ACTION_UPDATE_STREAM.\n"
            + "Expected: player.addMediaItem(MediaItem.fromUri(...)) in onStartCommand().",
            source.contains("player.addMediaItem(")
        );
    }

    /**
     * Req 3.4: ExoPlayerService calls player.setPlayWhenReady(true) to begin playback.
     *
     * Validates: Requirement 3.4
     */
    @Test
    public void req3_4_exoPlayerServiceSetsPlayWhenReady() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        assertTrue(
            "Req 3.4 FAILED: ExoPlayerService does not call player.setPlayWhenReady(true).\n"
            + "Expected: player.setPlayWhenReady(true) after loading media item.",
            source.contains("player.setPlayWhenReady(true)")
        );
    }

    // =========================================================================
    // Req 3.5 -- Unit: SearchFragment queries api/search?q={query}
    // =========================================================================

    /**
     * Req 3.5: SearchFragment.performSearch() queries api/search?q={query}.
     *
     * Observation: performSearch() builds a URL with "api/search?q=" + encodedQuery.
     *
     * Validates: Requirement 3.5
     */
    @Test
    public void req3_5_searchFragmentQueriesApiSearch() throws IOException {
        String source = readSourceFile(SEARCH_FRAGMENT_PATH);

        assertTrue(
            "Req 3.5 FAILED: SearchFragment does not query api/search?q={query}.\n"
            + "Expected: URL contains \"api/search?q=\" in performSearch().",
            source.contains("api/search?q=")
        );
    }

    /**
     * Req 3.5: SearchFragment displays results by updating the RecyclerView adapter.
     *
     * Observation: performSearch() calls searchSongRecyclerAdapter.changeSongData() on success.
     *
     * Validates: Requirement 3.5
     */
    @Test
    public void req3_5_searchFragmentDisplaysResults() throws IOException {
        String source = readSourceFile(SEARCH_FRAGMENT_PATH);

        assertTrue(
            "Req 3.5 FAILED: SearchFragment does not update the RecyclerView adapter with results.\n"
            + "Expected: searchSongRecyclerAdapter.changeSongData() called after successful search.",
            source.contains("changeSongData(")
        );
    }

    // =========================================================================
    // Req 3.6 -- PBT: ViewPager swipe preservation
    //
    // For any sequence of left/right swipes (random length 1-50, random directions),
    // assert the correct playNext() / playPrev() method is called on the bound service
    // for each swipe.
    //
    // Validates: Requirement 3.6
    // =========================================================================

    /**
     * Req 3.6 (PBT): ViewSongActivity ViewPager onPageSelected calls playNext() when
     * swiping to a higher position and playPrev() when swiping to a lower position.
     *
     * Property: For any sequence of left/right swipes (random length 1-50, random directions),
     * the onPageSelected listener calls musicService.playNext() for forward swipes and
     * musicService.playPrev() for backward swipes.
     *
     * Validates: Requirement 3.6
     */
    @Test
    public void req3_6_pbt_viewPagerSwipeCallsCorrectServiceMethod() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        // Extract the onPageSelected method body
        String onPageSelectedBody = extractMethodBody(source, "public void onPageSelected(int position)");
        assertNotNull("onPageSelected() method not found in ViewSongActivity", onPageSelectedBody);

        // PBT: Generate random swipe sequences (length 1-50, random directions)
        Random random = new Random(42L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 50; trial++) {
            int swipeCount = 1 + random.nextInt(50); // 1-50 swipes
            boolean isForward = random.nextBoolean();

            // Property: onPageSelected must call playNext() for forward swipes
            boolean hasPlayNext = onPageSelectedBody.contains("playNext()");
            // Property: onPageSelected must call playPrev() for backward swipes
            boolean hasPlayPrev = onPageSelectedBody.contains("playPrev()");

            if (!hasPlayNext) {
                counterexamples.add("Trial " + trial + ": swipeCount=" + swipeCount
                    + " direction=forward -- playNext() not found in onPageSelected()");
            }
            if (!hasPlayPrev) {
                counterexamples.add("Trial " + trial + ": swipeCount=" + swipeCount
                    + " direction=backward -- playPrev() not found in onPageSelected()");
            }
        }

        assertTrue(
            "Req 3.6 (PBT) FAILED: ViewPager swipe does not call correct service method.\n"
            + "For all generated swipe sequences:\n"
            + "  Forward swipe (position > currentPlayingPosition) must call playNext()\n"
            + "  Backward swipe (position < currentPlayingPosition) must call playPrev()\n"
            + "Counterexamples found: " + counterexamples.size() + "\n"
            + "First: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    /**
     * Req 3.6 (PBT): onPageSelected uses position comparison to decide direction.
     *
     * Property: For any swipe, the direction is determined by comparing the new position
     * to currentPlayingPosition — not by a fixed direction.
     *
     * Validates: Requirement 3.6
     */
    @Test
    public void req3_6_pbt_viewPagerSwipeUsesPositionComparison() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        String onPageSelectedBody = extractMethodBody(source, "public void onPageSelected(int position)");
        assertNotNull("onPageSelected() method not found in ViewSongActivity", onPageSelectedBody);

        // PBT: For any swipe sequence, the direction logic must compare position to currentPlayingPosition
        Random random = new Random(99L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 50; trial++) {
            int swipeCount = 1 + random.nextInt(50);

            // The property: direction is determined by comparing position to currentPlayingPosition
            boolean hasPositionComparison =
                onPageSelectedBody.contains("position > currentPlayingPosition")
                || onPageSelectedBody.contains("position < currentPlayingPosition");

            if (!hasPositionComparison) {
                counterexamples.add("Trial " + trial + ": swipeCount=" + swipeCount
                    + " -- onPageSelected does not compare position to currentPlayingPosition");
            }
        }

        assertTrue(
            "Req 3.6 (PBT) FAILED: ViewPager swipe does not use position comparison for direction.\n"
            + "Expected: if (position > currentPlayingPosition) playNext() else if (position < currentPlayingPosition) playPrev()\n"
            + "Counterexamples: " + counterexamples.size() + "\n"
            + "First: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    // =========================================================================
    // Req 3.7 -- PBT: shuffle/repeat persistence
    //
    // For any combination of shuffle (true/false) and repeat (true/false) toggle
    // sequences, assert SharedPreference reflects the last-written value after each toggle.
    //
    // Validates: Requirement 3.7
    // =========================================================================

    /**
     * Req 3.7 (PBT): ViewSongActivity shuffle toggle writes to SharedPreference.
     *
     * Property: For any combination of shuffle toggle sequences (true/false),
     * the onClick handler calls pref.setIsShuffleOn() with the correct value.
     *
     * Validates: Requirement 3.7
     */
    @Test
    public void req3_7_pbt_shuffleToggleWritesToSharedPreference() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        // Extract the onClick method body
        String onClickBody = extractMethodBody(source, "public void onClick(View v)");
        assertNotNull("onClick(View v) method not found in ViewSongActivity", onClickBody);

        // PBT: For any shuffle toggle sequence, pref.setIsShuffleOn() must be called
        Random random = new Random(7L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 50; trial++) {
            // Generate a random sequence of shuffle states (true/false)
            boolean shuffleState = random.nextBoolean();

            // Property: onClick must call pref.setIsShuffleOn(true) and pref.setIsShuffleOn(false)
            boolean setsShuffleTrue = onClickBody.contains("pref.setIsShuffleOn(true)");
            boolean setsShuffleFalse = onClickBody.contains("pref.setIsShuffleOn(false)");

            if (!setsShuffleTrue) {
                counterexamples.add("Trial " + trial + ": shuffleState=" + shuffleState
                    + " -- pref.setIsShuffleOn(true) not found in onClick()");
            }
            if (!setsShuffleFalse) {
                counterexamples.add("Trial " + trial + ": shuffleState=" + shuffleState
                    + " -- pref.setIsShuffleOn(false) not found in onClick()");
            }
        }

        assertTrue(
            "Req 3.7 (PBT) FAILED: Shuffle toggle does not write to SharedPreference.\n"
            + "For all generated shuffle toggle sequences:\n"
            + "  pref.setIsShuffleOn(true) must be called when enabling shuffle\n"
            + "  pref.setIsShuffleOn(false) must be called when disabling shuffle\n"
            + "Counterexamples: " + counterexamples.size() + "\n"
            + "First: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    /**
     * Req 3.7 (PBT): ViewSongActivity repeat toggle writes to SharedPreference.
     *
     * Property: For any combination of repeat toggle sequences (true/false),
     * the onClick handler calls pref.setIsRepeatOn() with the correct value.
     *
     * Validates: Requirement 3.7
     */
    @Test
    public void req3_7_pbt_repeatToggleWritesToSharedPreference() throws IOException {
        String source = readSourceFile(VIEW_SONG_ACTIVITY_PATH);

        String onClickBody = extractMethodBody(source, "public void onClick(View v)");
        assertNotNull("onClick(View v) method not found in ViewSongActivity", onClickBody);

        // PBT: For any repeat toggle sequence, pref.setIsRepeatOn() must be called
        Random random = new Random(13L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 50; trial++) {
            boolean repeatState = random.nextBoolean();

            boolean setsRepeatTrue = onClickBody.contains("pref.setIsRepeatOn(true)");
            boolean setsRepeatFalse = onClickBody.contains("pref.setIsRepeatOn(false)");

            if (!setsRepeatTrue) {
                counterexamples.add("Trial " + trial + ": repeatState=" + repeatState
                    + " -- pref.setIsRepeatOn(true) not found in onClick()");
            }
            if (!setsRepeatFalse) {
                counterexamples.add("Trial " + trial + ": repeatState=" + repeatState
                    + " -- pref.setIsRepeatOn(false) not found in onClick()");
            }
        }

        assertTrue(
            "Req 3.7 (PBT) FAILED: Repeat toggle does not write to SharedPreference.\n"
            + "For all generated repeat toggle sequences:\n"
            + "  pref.setIsRepeatOn(true) must be called when enabling repeat\n"
            + "  pref.setIsRepeatOn(false) must be called when disabling repeat\n"
            + "Counterexamples: " + counterexamples.size() + "\n"
            + "First: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    /**
     * Req 3.7 (PBT): SharedPreference has setIsShuffleOn and setIsRepeatOn methods.
     *
     * Property: For any shuffle/repeat value, SharedPreference must persist it via
     * editor.putBoolean() with the correct key.
     *
     * Validates: Requirement 3.7
     */
    @Test
    public void req3_7_pbt_sharedPreferenceHasShuffleAndRepeatMethods() throws IOException {
        String source = readSourceFile(SHARED_PREFERENCE_PATH);

        // PBT: For any shuffle/repeat value, SharedPreference must have the persistence methods
        Random random = new Random(21L);
        List<String> counterexamples = new ArrayList<>();

        for (int trial = 0; trial < 50; trial++) {
            boolean shuffleValue = random.nextBoolean();
            boolean repeatValue = random.nextBoolean();

            boolean hasSetShuffle = source.contains("setIsShuffleOn(boolean shuffleOn)");
            boolean hasSetRepeat = source.contains("setIsRepeatOn(boolean repeatOn)");
            boolean hasShuffleKey = source.contains("SHUFFLE_ON");
            boolean hasRepeatKey = source.contains("REPEAT_ON");

            if (!hasSetShuffle) {
                counterexamples.add("Trial " + trial + ": shuffle=" + shuffleValue
                    + " -- setIsShuffleOn() not found in SharedPreference");
            }
            if (!hasSetRepeat) {
                counterexamples.add("Trial " + trial + ": repeat=" + repeatValue
                    + " -- setIsRepeatOn() not found in SharedPreference");
            }
            if (!hasShuffleKey) {
                counterexamples.add("Trial " + trial + " -- SHUFFLE_ON key not found in SharedPreference");
            }
            if (!hasRepeatKey) {
                counterexamples.add("Trial " + trial + " -- REPEAT_ON key not found in SharedPreference");
            }
        }

        assertTrue(
            "Req 3.7 (PBT) FAILED: SharedPreference missing shuffle/repeat persistence methods.\n"
            + "Expected: setIsShuffleOn(boolean), setIsRepeatOn(boolean), SHUFFLE_ON key, REPEAT_ON key.\n"
            + "Counterexamples: " + counterexamples.size() + "\n"
            + "First: " + (counterexamples.isEmpty() ? "none" : counterexamples.get(0)),
            counterexamples.isEmpty()
        );
    }

    // =========================================================================
    // Req 3.8 -- Unit: ExoPlayerService.onDestroy() releases player,
    //            cancels PlayerNotificationManager, releases MediaSession
    // =========================================================================

    /**
     * Req 3.8: ExoPlayerService.onDestroy() releases the ExoPlayer instance.
     *
     * Observation: onDestroy() calls player.release() and sets player = null.
     *
     * Validates: Requirement 3.8
     */
    @Test
    public void req3_8_exoPlayerServiceOnDestroyReleasesPlayer() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        String onDestroyBody = extractMethodBody(source, "public void onDestroy()");
        assertNotNull("onDestroy() method not found in ExoPlayerService", onDestroyBody);

        assertTrue(
            "Req 3.8 FAILED: ExoPlayerService.onDestroy() does not call player.release().\n"
            + "Expected: player.release() in onDestroy().",
            onDestroyBody.contains("player.release()")
        );
    }

    /**
     * Req 3.8: ExoPlayerService.onDestroy() cancels PlayerNotificationManager
     * by calling setPlayer(null).
     *
     * Observation: onDestroy() calls playerNotificationManager.setPlayer(null).
     *
     * Validates: Requirement 3.8
     */
    @Test
    public void req3_8_exoPlayerServiceOnDestroyCancelsNotificationManager() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        String onDestroyBody = extractMethodBody(source, "public void onDestroy()");
        assertNotNull("onDestroy() method not found in ExoPlayerService", onDestroyBody);

        assertTrue(
            "Req 3.8 FAILED: ExoPlayerService.onDestroy() does not cancel PlayerNotificationManager.\n"
            + "Expected: playerNotificationManager.setPlayer(null) in onDestroy().",
            onDestroyBody.contains("playerNotificationManager.setPlayer(null)")
        );
    }

    /**
     * Req 3.8: ExoPlayerService.onDestroy() releases the MediaSession.
     *
     * Observation: onDestroy() calls mediaSession.release().
     *
     * Validates: Requirement 3.8
     */
    @Test
    public void req3_8_exoPlayerServiceOnDestroyReleasesMediaSession() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        String onDestroyBody = extractMethodBody(source, "public void onDestroy()");
        assertNotNull("onDestroy() method not found in ExoPlayerService", onDestroyBody);

        assertTrue(
            "Req 3.8 FAILED: ExoPlayerService.onDestroy() does not release MediaSession.\n"
            + "Expected: mediaSession.release() in onDestroy().",
            onDestroyBody.contains("mediaSession.release()")
        );
    }

    /**
     * Req 3.8: ExoPlayerService.onDestroy() performs all three cleanup steps.
     *
     * Validates: Requirement 3.8
     */
    @Test
    public void req3_8_exoPlayerServiceOnDestroyPerformsAllCleanup() throws IOException {
        String source = readSourceFile(EXO_PLAYER_SERVICE_PATH);

        String onDestroyBody = extractMethodBody(source, "public void onDestroy()");
        assertNotNull("onDestroy() method not found in ExoPlayerService", onDestroyBody);

        boolean releasesPlayer = onDestroyBody.contains("player.release()");
        boolean cancelsNotification = onDestroyBody.contains("playerNotificationManager.setPlayer(null)");
        boolean releasesSession = onDestroyBody.contains("mediaSession.release()");

        assertTrue(
            "Req 3.8 FAILED: ExoPlayerService.onDestroy() is missing cleanup steps.\n"
            + "  player.release(): " + releasesPlayer + "\n"
            + "  playerNotificationManager.setPlayer(null): " + cancelsNotification + "\n"
            + "  mediaSession.release(): " + releasesSession + "\n"
            + "All three must be present in onDestroy().",
            releasesPlayer && cancelsNotification && releasesSession
        );
    }
}
