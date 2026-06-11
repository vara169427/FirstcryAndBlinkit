package iq.stock_checker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class WebController {

    @Autowired
    private StockMonitorService service;

    @PostMapping("/start")
    public void start(@RequestBody(required = false) String body) {
        if (body == null || body.trim().isEmpty()) {
            service.addLiveLog("⚠️ No URLs provided");
            return;
        }
        List<String> urls = List.of(body.split("\\R"));
        service.start(urls);
    }

    @PostMapping("/stop")
    public void stop() {
        service.stop();
    }

    @GetMapping("/logs/live")
    public List<String> liveLogs() {
        return service.getLiveLogs();
    }

    @GetMapping("/logs/buynow")
    public List<String> buyNowLogs() {
        return service.getBuyNowLogs();
    }

    @PostMapping("/logs/live/clear")
    public void clearLive() {
        service.clearLiveLogs();
    }

    @PostMapping("/logs/buynow/clear")
    public void clearBuyNow() {
        service.clearBuyNowLogs();
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        return Map.of(
            "botToken", service.getBotToken() == null ? "" : service.getBotToken(),
            "chatId", service.getChatId() == null ? "" : service.getChatId(),
            "proxyServer", service.getProxyServer() == null ? "" : service.getProxyServer(),
            "defaultSelector", service.getDefaultSelector() == null ? "" : service.getDefaultSelector(),
            "engine", service.getEngine() == null ? "" : service.getEngine(),
            "checkIntervalMs", service.getCheckIntervalMs(),
            "running", service.isRunning(),
            "latitude", service.getLatitude() == null ? "" : service.getLatitude(),
            "longitude", service.getLongitude() == null ? "" : service.getLongitude()
        );
    }

    @PostMapping("/settings")
    public void updateSettings(@RequestBody Map<String, Object> body) {
        if (body.containsKey("botToken")) {
            service.setBotToken((String) body.get("botToken"));
        }
        if (body.containsKey("chatId")) {
            service.setChatId((String) body.get("chatId"));
        }
        if (body.containsKey("proxyServer")) {
            service.setProxyServer((String) body.get("proxyServer"));
        }
        if (body.containsKey("defaultSelector")) {
            service.setDefaultSelector((String) body.get("defaultSelector"));
        }
        if (body.containsKey("engine")) {
            service.setEngine((String) body.get("engine"));
        }
        if (body.containsKey("checkIntervalMs")) {
            Object interval = body.get("checkIntervalMs");
            if (interval != null) {
                service.setCheckIntervalMs(Integer.parseInt(interval.toString()));
            }
        }
        if (body.containsKey("latitude")) {
            Object latObj = body.get("latitude");
            if (latObj != null && !latObj.toString().trim().isEmpty()) {
                service.setLatitude(Double.parseDouble(latObj.toString()));
            } else {
                service.setLatitude(null);
            }
        }
        if (body.containsKey("longitude")) {
            Object lonObj = body.get("longitude");
            if (lonObj != null && !lonObj.toString().trim().isEmpty()) {
                service.setLongitude(Double.parseDouble(lonObj.toString()));
            } else {
                service.setLongitude(null);
            }
        }
    }

    @GetMapping(value = "/debug/screenshot")
    public org.springframework.http.ResponseEntity<?> getDebugScreenshot() {
        byte[] screenshot = StockMonitorService.getLastScreenshot();
        if (screenshot == null) {
            return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.OK)
                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                .body("<html><body><h2>No screenshot captured yet. Please start the monitoring service and wait for a check cycle to run.</h2></body></html>");
        }
        return org.springframework.http.ResponseEntity
            .status(org.springframework.http.HttpStatus.OK)
            .contentType(org.springframework.http.MediaType.IMAGE_PNG)
            .body(screenshot);
    }
}
