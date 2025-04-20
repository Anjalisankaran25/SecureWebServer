import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.concurrent.*;

public class SecureWebServer {
    public static void main(String[] args) throws IOException {
        int port = 8086;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RequestHandler());
        server.setExecutor(Executors.newFixedThreadPool(10)); // Thread pool for concurrency
        System.out.println("Secure Web Server started on port " + port);
        server.start();
    }
}

class RequestHandler implements HttpHandler {
    private static final File BASE_DIR;

    static {
        File dir = new File("www");
        File resolved;
        try {
            resolved = dir.getCanonicalFile();
        } catch (IOException e) {
            resolved = dir.getAbsoluteFile(); // fallback
        }
        BASE_DIR = resolved;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGetRequest(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePostRequest(exchange);
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), "UTF-8");

        // Prevent directory traversal
        File requestedFile = new File(BASE_DIR, path).getCanonicalFile();
        if (!requestedFile.getPath().startsWith(BASE_DIR.getPath())) {
            sendResponse(exchange, 403, "Access Denied");
            return;
        }

        if ("/".equals(path)) {
            String html = "<html><body><h2>Welcome to Secure Web Server</h2>" +
                    "<form method='POST'>" +
                    "Name: <input type='text' name='name'><br>" +
                    "Message: <input type='text' name='message'><br>" +
                    "<input type='submit' value='Send'>" +
                    "</form></body></html>";
            sendResponse(exchange, 200, html, "text/html");
        } else if (requestedFile.exists() && requestedFile.isFile()) {
            String contentType = guessContentType(requestedFile.getName());
            byte[] bytes = Files.readAllBytes(requestedFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            sendResponse(exchange, 404, "404 Not Found");
        }
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");

        // Basic input sanitization
        body = sanitize(body);
        logRequest(body);
        sendResponse(exchange, 200, "Form submitted successfully.");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        sendResponse(exchange, statusCode, response, "text/plain");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String guessContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private String sanitize(String input) {
        return input.replaceAll("[<>]", ""); // Very basic XSS prevention
    }

    private void logRequest(String data) {
        System.out.println("[LOG] Received: " + data);
    }
}
