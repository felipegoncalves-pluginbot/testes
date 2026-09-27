package com.felipe.elfmirror;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

/** App auxiliar: conecta ao Sanbot e processa pose para o modo espelho. */
public class MainActivity extends AppCompatActivity implements MirrorAuxSession.UiCallback {

  private TextInputEditText hostIpInput;
  private Button connectButton;
  private TextView statusText;
  private TextView metricsText;

  private MirrorAuxSession session;
  private SharedPreferences prefs;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    prefs = getSharedPreferences(getPackageName() + "_prefs", MODE_PRIVATE);
    hostIpInput = findViewById(R.id.hostIpInput);
    connectButton = findViewById(R.id.connectButton);
    statusText = findViewById(R.id.statusText);
    metricsText = findViewById(R.id.metricsText);

    hostIpInput.setText(prefs.getString(getString(R.string.pref_host_ip), ""));

    String deviceName = Build.MODEL != null ? Build.MODEL : "android-aux";
    session = new MirrorAuxSession(deviceName);
    session.setUiCallback(this);

    connectButton.setOnClickListener(v -> toggleConnection());
  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      session.release();
    }
    super.onDestroy();
  }

  private void toggleConnection() {
    if (session.isConnected()) {
      session.disconnect();
      connectButton.setText(R.string.connect);
      hostIpInput.setEnabled(true);
      return;
    }

    String hostIp = hostIpInput.getText() != null ? hostIpInput.getText().toString().trim() : "";
    if (hostIp.isEmpty()) {
      hostIpInput.setError(getString(R.string.host_ip_hint));
      return;
    }

    prefs.edit().putString(getString(R.string.pref_host_ip), hostIp).apply();
    hostIpInput.setEnabled(false);
    connectButton.setText(R.string.disconnect);
    session.connect(hostIp);
  }

  @Override
  public void onStatus(String status) {
    statusText.setText(status);
    if (status.startsWith("Desconectado")) {
      connectButton.setText(R.string.connect);
      hostIpInput.setEnabled(true);
    }
  }

  @Override
  public void onMetrics(long frames, long tracks, long lastSeq, String lastError) {
    StringBuilder sb = new StringBuilder();
    sb.append("frames=").append(frames);
    sb.append("  tracks=").append(tracks);
    sb.append("  seq=").append(lastSeq);
    if (lastError != null && !lastError.isEmpty()) {
      sb.append("\nultimo erro: ").append(lastError);
    }
    metricsText.setText(sb.toString());
  }
}
