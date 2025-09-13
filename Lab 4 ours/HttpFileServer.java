import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpFileServer {
    private static final int PORT = 8080;
    private static final String BASE_DIR = "server_files"; // folder to store files

    public static void main(String[] args) throws IOException {
        // Create base directory if not exists
        File dir = new File(BASE_DIR);
        if (!dir.exists()) dir.mkdir();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Define contexts (routes)
        server.createContext("/download", new DownloadHandler());
        server.createContext("/upload", new UploadHandler());

        // Thread pool for handling multiple clients
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        server.setExecutor(threadPool);

        System.out.println("HTTP File Server started on port " + PORT);
        server.start();
    }

    // --------- Download Handler (GET) ----------
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }

            URI requestURI = exchange.getRequestURI();
            String query = requestURI.getQuery(); // e.g., filename=test.txt
            String filename = null;
            if (query != null && query.startsWith("filename=")) {
                filename = query.substring("filename=".length());
            }

            if (filename == null) {
                String msg = "Missing filename parameter";
                exchange.sendResponseHeaders(400, msg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg.getBytes());
                }
                return;
            }

            File file = new File(BASE_DIR, filename);
            if (!file.exists() || !file.isFile()) {
                String msg = "File Not Found";
                exchange.sendResponseHeaders(404, msg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg.getBytes());
                }
                return;
            }

            // Set headers
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // --------- Upload Handler (POST) ----------
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }

            // Save file with timestamp
            String filename = "upload_" + System.currentTimeMillis();
            File file = new File(BASE_DIR, filename);

            try (InputStream is = exchange.getRequestBody();
                 FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            String response = "File uploaded successfully as: " + filename;
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
