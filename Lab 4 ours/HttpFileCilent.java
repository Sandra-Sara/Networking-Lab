import java.io.*;
import java.net.*;
import java.util.Scanner;

public class HttpFileClient {
    private static final String SERVER_URL = "http://127.0.0.1:8080";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Choose an option: ");
        System.out.println("1. Upload file");
        System.out.println("2. Download file");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        if (choice == 1) {
            System.out.print("Enter path of file to upload: ");
            String filePath = scanner.nextLine();
            uploadFile(filePath);
        } else if (choice == 2) {
            System.out.print("Enter filename to download: ");
            String fileName = scanner.nextLine();
            downloadFile(fileName);
        } else {
            System.out.println("Invalid choice");
        }
    }

    // -------- Upload (POST) --------
    private static void uploadFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        URL url = new URL(SERVER_URL + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        // Get response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        conn.disconnect();
    }

    // -------- Download (GET) --------
    private static void downloadFile(String fileName) throws Exception {
        URL url = new URL(SERVER_URL + "/download?filename=" + URLEncoder.encode(fileName, "UTF-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();

        if (responseCode == 200) {
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream("downloaded_" + fileName)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("File downloaded successfully as downloaded_" + fileName);
        } else if (responseCode == 404) {
            System.out.println("File not found on server.");
        } else {
            System.out.println("Server returned response code: " + responseCode);
        }

        conn.disconnect();
    }
}
