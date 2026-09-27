package com.felipe.elftemplate;

import android.content.Intent;
import android.os.Bundle;
import androidx.cardview.widget.CardView;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;

/**
 * Menu principal do aplicativo. Hub de Jogos Kinect e Modo Espelho / Marionete do robô Sanbot Elf.
 */
public class MainActivity extends BindBaseActivity {

  private SpeechManager speechManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(MainActivity.class);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    initUI();
  }

  private void initUI() {
    CardView cardMirror = findViewById(R.id.card_mirror);
    if (cardMirror != null) {
      cardMirror.setOnClickListener(
          v -> {
            startActivity(new Intent(MainActivity.this, MirrorActivity.class));
          });
    }

    CardView cardTennis = findViewById(R.id.card_tennis);
    cardTennis.setOnClickListener(
        v -> {
          startActivity(new Intent(MainActivity.this, TennisActivity.class));
        });

    CardView cardFruitNinja = findViewById(R.id.card_fruit_ninja);
    cardFruitNinja.setOnClickListener(
        v -> {
          Intent intent = new Intent(MainActivity.this, KinectWebGameActivity.class);
          intent.putExtra(
              KinectWebGameActivity.EXTRA_GAME_URL,
              "file:///android_asset/games/fruit_ninja/index.html");
          intent.putExtra(KinectWebGameActivity.EXTRA_GAME_TITLE, "Ninja das Frutas");
          startActivity(intent);
        });

    CardView cardPoseMatch = findViewById(R.id.card_pose_match);
    cardPoseMatch.setOnClickListener(
        v -> {
          startActivity(new Intent(MainActivity.this, PoseMatchActivity.class));
        });

    CardView cardDodger = findViewById(R.id.card_dodger);
    cardDodger.setOnClickListener(
        v -> {
          Intent intent = new Intent(MainActivity.this, KinectWebGameActivity.class);
          intent.putExtra(
              KinectWebGameActivity.EXTRA_GAME_URL,
              "file:///android_asset/games/flappy_pose/index.html");
          intent.putExtra(KinectWebGameActivity.EXTRA_GAME_TITLE, "Flappy Pose");
          startActivity(intent);
        });

    CardView cardFollow = findViewById(R.id.card_follow);
    cardFollow.setOnClickListener(
        v -> {
          startActivity(new Intent(MainActivity.this, FollowActivity.class));
        });

    CardView cardCpr = findViewById(R.id.card_cpr);
    if (cardCpr != null) {
      cardCpr.setOnClickListener(
          v -> {
            startActivity(new Intent(MainActivity.this, CprActivity.class));
          });
    }

    CardView cardHandCursor = findViewById(R.id.card_hand_cursor);
    if (cardHandCursor != null) {
      cardHandCursor.setOnClickListener(
          v -> {
            startActivity(new Intent(MainActivity.this, HandCursorActivity.class));
          });
    }

    CardView cardTracking3d = findViewById(R.id.card_tracking3d);
    if (cardTracking3d != null) {
      cardTracking3d.setOnClickListener(
          v -> {
            startActivity(new Intent(MainActivity.this, Tracking3dActivity.class));
          });
    }
  }

  @Override
  public void onMainServiceConnected() {
    speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    if (speechManager != null) {
      speechManager.startSpeak(
          "Bem-vindo ao hub da Ciência da Computação! Escolha um jogo nas laterais.");
    }
  }
}
