package com.felipe.elftemplate.server;

import com.felipe.elftemplate.movement.Waypoint;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serializador JSON de telemetria e estado para o RobotWebServer. */
public class RobotTelemetrySerializer {

  public static String buildTelemetryJson(RobotWebServer.MapDataProvider provider) {
    if (provider == null) return "{}";
    try {
      JSONObject json = new JSONObject();
      json.put("x", provider.getX());
      json.put("y", provider.getY());
      json.put("yaw", provider.getYaw());
      json.put("isRecording", provider.isRecording());
      json.put("isNavigating", provider.isNavigating());
      json.put("isStopped", provider.isStopped());
      json.put("rawGyro", provider.getRawGyro());
      json.put("astraFrames", provider.getAstraFrames());
      json.put("isAstraActive", provider.isAstraActive());

      JSONArray pathArr = new JSONArray();
      List<float[]> path = provider.getPath();
      if (path != null) {
        for (float[] p : path) {
          JSONArray pt = new JSONArray();
          pt.put(p[0]);
          pt.put(p[1]);
          pathArr.put(pt);
        }
      }
      json.put("path", pathArr);

      JSONArray wpArr = new JSONArray();
      List<Waypoint> waypoints = provider.getWaypoints();
      if (waypoints != null) {
        for (Waypoint wp : waypoints) {
          JSONObject obj = new JSONObject();
          obj.put("name", wp.getName());
          obj.put("x", wp.getX());
          obj.put("y", wp.getY());
          wpArr.put(obj);
        }
      }
      json.put("waypoints", wpArr);
      return json.toString();
    } catch (JSONException e) {
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }
}
