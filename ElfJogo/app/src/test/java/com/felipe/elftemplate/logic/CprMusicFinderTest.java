package com.felipe.elftemplate.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CprMusicFinderTest {

  @Test
  public void testPickBestMp3PrefersCprKeywords() {
    List<String> files =
        Arrays.asList(
            "/sdcard/Bluetooth/random_song.mp3",
            "/sdcard/Download/rcp_treino.mp3",
            "/sdcard/cpr_metronome_stayin.mp3");
    String best = com.felipe.elftemplate.logic.CprMusicFinder.pickBestMp3(files);
    assertEquals("/sdcard/cpr_metronome_stayin.mp3", best);
  }

  @Test
  public void testBluetoothFolderIsPrioritySearchRoot() {
    assertEquals("Bluetooth", CprMusicFinder.PRIORITY_FOLDERS[0]);
  }

  @Test
  public void testSystemPickerDisplayNameFallback() {
    assertEquals("Música selecionada", CprSystemAudioPicker.resolveDisplayName(null, null));
    assertEquals(4201, CprSystemAudioPicker.REQUEST_PICK_AUDIO);
  }

  @Test
  public void testPickBestMp3ReturnsNullWhenEmpty() {
    assertNull(com.felipe.elftemplate.logic.CprMusicFinder.pickBestMp3(null));
    assertNull(com.felipe.elftemplate.logic.CprMusicFinder.pickBestMp3(Arrays.<String>asList()));
  }

  @Test
  public void testPickBestMp3RanksMultipleCandidates() {
    String[] paths =
        new String[] {
          "/sdcard/Music/random.mp3",
          "/sdcard/Download/ressuscitacao.mp3",
          "/sdcard/rcp.mp3"
        };
    List<String> ranked = new ArrayList<String>();
    for (int i = 0; i < paths.length; i++) {
      ranked.add(paths[i]);
    }
    String best = CprMusicFinder.pickBestMp3(ranked);
    assertEquals("/sdcard/rcp.mp3", best);
    assertEquals(3, ranked.size());
  }
}
