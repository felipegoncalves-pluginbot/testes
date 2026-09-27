package com.felipe.elftemplate.server;

import static org.junit.Assert.*;

import com.felipe.elftemplate.movement.RadarObject;
import com.felipe.elftemplate.movement.Waypoint;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RobotWebServerTest {

  private RobotWebServer server;
  private int port = 8085; // Use a different port than 8080 for testing

  private static class FakeRobotCommandListener implements RobotWebServer.RobotCommandListener {
    public String lastCommand;
    public String lastAction;

    @Override
    public void onCommandReceived(String command) {
      this.lastCommand = command;
    }

    @Override
    public void onActionReceived(String action) {
      this.lastAction = action;
    }
  }

  private static class FakeMapDataProvider implements RobotWebServer.MapDataProvider {
    @Override
    public double getX() {
      return 1.0;
    }

    @Override
    public double getY() {
      return 2.0;
    }

    @Override
    public double getYaw() {
      return 0.5;
    }

    @Override
    public boolean isRecording() {
      return false;
    }

    @Override
    public boolean isNavigating() {
      return false;
    }

    @Override
    public List<Waypoint> getWaypoints() {
      List<Waypoint> list = new ArrayList<>();
      list.add(new Waypoint("PontoA", 1.0, 2.0));
      return list;
    }

    @Override
    public List<float[]> getPath() {
      return new ArrayList<>();
    }

    @Override
    public List<float[]> getKnownPoints() {
      return new ArrayList<>();
    }

    @Override
    public List<float[]> getWallPoints() {
      return new ArrayList<>();
    }

    @Override
    public List<RadarObject> getRadarObjects() {
      return new ArrayList<>();
    }

    @Override
    public int[] getInfrared() {
      return new int[18];
    }

    @Override
    public double getRawGyro() {
      return 0.0;
    }

    @Override
    public float[] getAccel() {
      return new float[3];
    }

    @Override
    public byte[] getLatestFrame() {
      return null;
    }

    @Override
    public byte[] getOccupancyGrid() {
      return null;
    }

    @Override
    public int getAstraFrames() {
      return 0;
    }

    @Override
    public boolean isAstraActive() {
      return false;
    }

    @Override
    public boolean isStopped() {
      return true;
    }
  }

  @Before
  public void setUp() throws Exception {
    FakeRobotCommandListener listener = new FakeRobotCommandListener();
    FakeMapDataProvider provider = new FakeMapDataProvider();
    // Pass null context since we don't need real Android assets in these tests
    server = new RobotWebServer(null, port, listener, provider);
    server.start();
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testGzipDisabledForMapData() throws Exception {
    URL url = new URL("http://localhost:" + port + "/telemetry");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Accept-Encoding", "gzip");
    conn.connect();

    int responseCode = conn.getResponseCode();
    assertEquals(200, responseCode);

    // Content-Encoding should not be gzip since we explicitly disabled it
    String contentEncoding = conn.getContentEncoding();
    assertNotEquals("gzip", contentEncoding);

    // Verify response contains map data
    InputStream is = conn.getInputStream();
    byte[] buffer = new byte[1024];
    int read = is.read(buffer);
    assertTrue(read > 0);
    String responseBody = new String(buffer, 0, read);
    assertTrue(responseBody.contains("\"x\":1"));
    assertTrue(responseBody.contains("\"y\":2"));
  }

  @Test
  public void testGracefulHandlingOnPrematureClientClose() throws Exception {
    // We simulate a client socket closing right after sending headers
    Socket socket = new Socket("localhost", port);
    OutputStream os = socket.getOutputStream();
    os.write(
        "GET /telemetry HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\n\r\n".getBytes());
    os.flush();
    socket.close(); // Client disconnects abruptly

    // Allow some time for server to try writing and encounter exception
    Thread.sleep(100);

    // Verify the server is still alive and responsive
    URL url = new URL("http://localhost:" + port + "/telemetry");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    assertEquals(200, conn.getResponseCode());
  }
}
