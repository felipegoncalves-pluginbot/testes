package com.felipe.elftemplate.server;

import android.content.Context;
import com.felipe.elftemplate.movement.RadarObject;
import com.felipe.elftemplate.movement.Waypoint;
import fi.iki.elonen.NanoHTTPD;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Servidor HTTP embarcado NanoHTTPD para controle WASD e visualização do mapa. */
public class RobotWebServer extends NanoHTTPD {

  private final RobotCommandListener commandListener;
  private final MapDataProvider mapDataProvider;
  private final Context context;
  private String htmlTemplate = "";

  public interface RobotCommandListener {
    void onCommandReceived(String command);

    void onActionReceived(String action);
  }

  public interface MapDataProvider {
    double getX();

    double getY();

    double getYaw();

    boolean isRecording();

    boolean isNavigating();

    List<Waypoint> getWaypoints();

    List<float[]> getPath();

    List<float[]> getKnownPoints();

    List<float[]> getWallPoints();

    List<RadarObject> getRadarObjects();

    int[] getInfrared();

    double getRawGyro();

    float[] getAccel();

    byte[] getLatestFrame();

    byte[] getOccupancyGrid();

    int getAstraFrames();

    boolean isAstraActive();

    boolean isStopped();
  }

  public RobotWebServer(
      Context context, int port, RobotCommandListener listener, MapDataProvider mapDataProvider) {
    super(port);
    this.context = context;
    this.commandListener = listener;
    this.mapDataProvider = mapDataProvider;
    try {
      InputStream is = context.getAssets().open("dashboard.html");
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      this.htmlTemplate = sb.toString();
    } catch (Exception e) {
      this.htmlTemplate = "Erro ao carregar UI: " + e.getMessage();
    }
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    Method method = session.getMethod();

    if (Method.GET.equals(method) && "/".equals(uri)) {
      return newFixedLengthResponse(Response.Status.OK, "text/html", htmlTemplate);
    } else if (Method.GET.equals(method) && "/telemetry".equals(uri)) {
      String json = RobotTelemetrySerializer.buildTelemetryJson(mapDataProvider);
      return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    } else if (Method.POST.equals(method) && "/command".equals(uri)) {
      Map<String, String> files = new HashMap<>();
      try {
        session.parseBody(files);
        String postData = files.get("postData");
        if (commandListener != null && postData != null) {
          commandListener.onCommandReceived(postData.trim());
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
      } catch (Exception e) {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
      }
    }
    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
  }

  @Override
  protected boolean useGzipWhenAccepted(Response r) {
    return false;
  }
}
