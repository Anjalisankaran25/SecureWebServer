import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.*;

public class SecureWebServer {
    public static void main(String[] args) throws IOException {
        int port = 8086;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RequestHandler());
        server.setExecutor(Executors.newFixedThreadPool(10)); // Thread pool for concurrency
        System.out.println("Server started on port " + port);
        server.start();
    }
}

class RequestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(requestMethod)) {
            handleGetRequest(exchange);
        } else if ("POST".equalsIgnoreCase(requestMethod)) {
            handlePostRequest(exchange);
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if ("/".equals(path)) {
        String html = "<html><body><h2>Welcome to Secure Web Server</h2>" +
                      "<form method='POST'>" +
                      "Name: <input type='text' name='name'><br>" +
                      "Message: <input type='text' name='message'><br>" +
                      "<input type='submit' value='Send'>" +
                      "</form></body></html>";
        sendResponse(exchange, 200, html);
        return;
    }

    // Handle static file access
    String filePath = "www" + path;
    File file = new File(filePath);
    if (file.exists() && !file.isDirectory()) {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    } else {
        sendResponse(exchange, 404, "404 Not Found");
    }
}


    private void handlePostRequest(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes());
        logRequest(body);
        sendResponse(exchange, 200, "Form submitted successfully.");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void logRequest(String data) {
        System.out.println("[LOG] Received Data: " + data);
    }
}
