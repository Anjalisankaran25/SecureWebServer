import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.logging.*;

public class SecureWebServer {
    private static final int PORT = 8086;
    public static final Logger LOGGER = Logger.getLogger("SecureWebServer");

    public static void main(String[] args) {
        try {
            setupLogger();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new RequestHandler());
            server.setExecutor(Executors.newFixedThreadPool(10));

            LOGGER.info("Secure Web Server started on port " + PORT);
            server.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Server failed to start", e);
        }
    }

    private static void setupLogger() throws IOException {
        FileHandler handler = new FileHandler("server.log", true);
        handler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
    }
}

class RequestHandler implements HttpHandler {
    private static final File BASE_DIR;

    static {
        File base;
        try {
            base = new File("www").getCanonicalFile();
        } catch (IOException e) {
            base = new File("www");
        }
        BASE_DIR = base;
    }

    @Override
    public void handle(HttpExchange exchange) {
        Runnable task = () -> {
            try {
                String method = exchange.getRequestMethod();
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (IOException e) {
                SecureWebServer.LOGGER.log(Level.SEVERE, "Request handling failed", e);
                try {
                    sendResponse(exchange, 500, "Internal Server Error");
                } catch (IOException ignored) {
                }
            }
        };

        new Thread(task).start();
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/".equals(path)) {
            String html = """
                <html>
                <head>
                    <title>Secure Web Server</title>
                    <style>
                        body { font-family: Arial, sans-serif; background: #f0f2f5; padding: 40px; }
                        form { background: #fff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); width: 500px; margin: auto; }
                        h2 { text-align: center; color: #333; }
                        input, textarea { width: 100%; padding: 12px; margin-top: 8px; margin-bottom: 20px; border: 1px solid #ccc; border-radius: 8px; }
                        input[type=submit] { background-color: #007bff; color: white; border: none; cursor: pointer; transition: 0.3s; }
                        input[type=submit]:hover { background-color: #0056b3; }
                        label { font-weight: bold; }
                    </style>
                </head>
                <body>
                    <form method='POST'>
                        <h2>Contact Us</h2>
                        <label>Name:</label>
                        <input type='text' name='name' required>
                        
                        <label>Email:</label>
                        <input type='email' name='email' required>
                        
                        <label>Phone:</label>
                        <input type='tel' name='phone' pattern='[0-9]{10}' required>
                        
                        <label>Subject:</label>
                        <input type='text' name='subject'>
                        
                        <label>Message:</label>
                        <textarea name='message' rows='5'></textarea>
                        
                        <input type='submit' value='Send Message'>
                    </form>
                </body>
                </html>
                """;
            sendResponse(exchange, 200, html);
            return;
        }

        File file = new File(BASE_DIR, path).getCanonicalFile();

        if (!file.getPath().startsWith(BASE_DIR.getPath())) {
            sendResponse(exchange, 403, "403 Forbidden");
            return;
        }

        if (file.exists() && !file.isDirectory()) {
            byte[] data = Files.readAllBytes(file.toPath());
            String contentType = Files.probeContentType(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType != null ? contentType : "application/octet-stream");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } else {
            sendResponse(exchange, 404, "404 Not Found");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes());

        SecureWebServer.LOGGER.info("Form submission received: " + body);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("submissions.txt", true))) {
            writer.write(body);
            writer.newLine();
        } catch (IOException e) {
            SecureWebServer.LOGGER.log(Level.WARNING, "Failed to save submission", e);
        }

        sendResponse(exchange, 200, "<h2>Thank you! Your submission has been received.</h2><a href='/'>Back to form</a>");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
