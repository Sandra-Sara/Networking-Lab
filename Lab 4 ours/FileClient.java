import java.io.*;
import java.net.*;

public class FileClient {
    private static final String SERVER_ADDRESS = "127.0.0.1"; // Localhost
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             DataInputStream dataIn = new DataInputStream(socket.getInputStream());
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            // Step 2: Receive server prompt
            System.out.print(serverReader.readLine() + " ");

            // Step 3: Input file name from user
            String fileName = userInput.readLine();
            serverWriter.write(fileName + "\n");
            serverWriter.flush();

            // Step 4: Check response
            String response = serverReader.readLine();

            if ("FOUND".equals(response)) {
                long fileSize = dataIn.readLong();
                System.out.println("File found. Size: " + fileSize + " bytes");

                // Save file locally
                FileOutputStream fileOut = new FileOutputStream("downloaded_" + fileName);
                byte[] buffer = new byte[4096];
                long bytesReceived = 0;
                int bytesRead;

                while (bytesReceived < fileSize && (bytesRead = dataIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                }

                fileOut.close();
                System.out.println("File downloaded successfully as downloaded_" + fileName);

            } else {
                System.out.println("File not found on server.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
