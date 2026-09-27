package com.sanbot.debug;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;

/** HTTP :8090 — /state.json /vision.png /rgb.jpg / (viewer). Sem gzip (Android 6). */
public class SanbotDebugServer extends NanoHTTPD {

  private static final String HTML =
      "<!doctype html><meta charset=utf-8><title>Sanbot debug</title>"
          + "<style>body{font-family:sans-serif;background:#111;color:#0f0;margin:12px}"
          + "img{background:#000;max-width:32%;image-rendering:pixelated;vertical-align:top}"
          + "pre{white-space:pre-wrap;font-size:12px}</style>"
          + "<h1>Sanbot debug</h1>"
          + "<p><img id=c src=/rgb.jpg width=320 height=240 alt=hd>"
          + "<img id=v src=/vision.png width=320 height=240 alt=astra></p>"
          + "<pre id=s>loading</pre>"
          + "<script>function t(){fetch('/state.json').then(r=>r.text()).then(t=>{"
          + "document.getElementById('s').textContent=t;var n=Date.now();"
          + "document.getElementById('v').src='/vision.png?t='+n;"
          + "document.getElementById('c').src='/rgb.jpg?t='+n}).catch(()=>{});"
          + "setTimeout(t,600)}t()</script>";

  private final SanbotDebugHub hub;

  public SanbotDebugServer(SanbotDebugHub hub, int port) {
    super("0.0.0.0", port);
    this.hub = hub;
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    Response r;
    if ("/".equals(uri) || "/index.html".equals(uri)) {
      r = newFixedLengthResponse(Response.Status.OK, "text/html", HTML);
    } else if ("/state.json".equals(uri) || "/debug/state.json".equals(uri)) {
      r = newFixedLengthResponse(Response.Status.OK, "application/json", hub.snapshotJson());
    } else if ("/vision.png".equals(uri) || "/debug/vision.png".equals(uri)) {
      r = fileResponse(hub.pngFile(), "image/png");
    } else if ("/rgb.jpg".equals(uri) || "/debug/rgb.jpg".equals(uri) || "/camera.jpg".equals(uri)) {
      r = fileResponse(hub.rgbFile(), "image/jpeg");
    } else if ("/health".equals(uri)) {
      r = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}");
    } else {
      r = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
    }
    r.addHeader("Access-Control-Allow-Origin", "*");
    r.addHeader("Cache-Control", "no-store");
    return r;
  }

  @Override
  protected boolean useGzipWhenAccepted(Response r) {
    return false;
  }

  private static Response fileResponse(File file, String mime) {
    if (file == null || !file.exists()) {
      return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "empty");
    }
    try {
      FileInputStream in = new FileInputStream(file);
      return newFixedLengthResponse(Response.Status.OK, mime, in, file.length());
    } catch (Exception e) {
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
    }
  }
}
