import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class HttpFileServer {

    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/list", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    File dir = new File("server_files");
                    if (!dir.exists()) dir.mkdir();

                    File[] files = dir.listFiles();
                    StringBuilder response = new StringBuilder();

                    if (files != null && files.length > 0) {
                        for (File f : files) {
                            if (f.isFile()) {
                                response.append(f.getName()).append("\n");
                            }
                        }
                        System.out.println(" Sent file list to client");
                    } else {
                        response.append("No files found.");
                        System.out.println(" No files found in server_files");
                    }

                    byte[] respBytes = response.toString().getBytes();
                    exchange.sendResponseHeaders(200, respBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(respBytes);
                    os.close();
                }
            }
        });

        // Handle GET: download file
        server.createContext("/download", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String fileName = query != null && query.startsWith("file=") ? query.substring(5) : "";
                File file = new File("server_files/" + fileName);

                if (file.exists()) {
                    exchange.sendResponseHeaders(200, file.length());
                    OutputStream os = exchange.getResponseBody();
                    Files.copy(file.toPath(), os);
                    os.close();
                    System.out.println(" File sent: " + fileName);
                } else {
                    String response = "File not found: " + fileName;
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    System.out.println(" File requested but not found: " + fileName);
                }
            }
        });

        // Handle POST: upload file
        server.createContext("/upload", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String fileName = exchange.getRequestHeaders().getFirst("File-Name");
                if (fileName == null) fileName = "uploaded_file";

                File uploadDir = new File("server_files");
                if (!uploadDir.exists()) uploadDir.mkdir();

                File file = new File(uploadDir, fileName);
                InputStream is = exchange.getRequestBody();
                Files.copy(is, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                is.close();

                String response = "File uploaded: " + fileName;
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

                System.out.println("File uploaded: " + fileName);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println(" HTTP File Server started at port " + port);
    }
}
