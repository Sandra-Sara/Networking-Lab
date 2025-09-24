import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Scanner;

public class HttpFileClient {

    private static final String SERVER_URL = "http://localhost:8000";

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter command (download <filename>, upload <filename>, quit): ");
            String command = scanner.nextLine();

            if (command.equalsIgnoreCase("quit")) {
                System.out.println("Exiting client...");
                break;
            } else if (command.startsWith("download ")) {
                String fileName = command.substring(9).trim();
                downloadFile(fileName);
            } else if (command.startsWith("upload ")) {
                String fileName = command.substring(7).trim();
                uploadFile(fileName);
            } else {
                System.out.println("Invalid command.");
            }
        }
        scanner.close();
    }

    private static void downloadFile(String fileName) throws IOException {
        URL url = new URL(SERVER_URL + "/download?file=" + fileName);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            File downloadDir = new File("client_files");
            if (!downloadDir.exists()) downloadDir.mkdir();

            InputStream is = connection.getInputStream();
            FileOutputStream fos = new FileOutputStream(new File(downloadDir, fileName));

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fos.close();
            is.close();
            System.out.println("File downloaded to client_files/" + fileName);
        } else {
            System.out.println("File not found on server.");
        }
    }

    private static void uploadFile(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("File does not exist locally.");
            return;
        }

        URL url = new URL(SERVER_URL + "/upload");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("File-Name", file.getName());

        OutputStream os = connection.getOutputStream();
        Files.copy(file.toPath(), os);
        os.close();

        InputStream is = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String response = reader.readLine();
        reader.close();
        System.out.println(response);
    }
}
