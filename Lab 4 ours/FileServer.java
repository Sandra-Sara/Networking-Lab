import java.io.*;
import java.net.*;
public class FileServer {
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("File Server started on port " + SERVER_PORT);

            while (true) {
                // Accept new client connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);

                // Create new thread for each client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Thread class for handling multiple clients
class ClientHandler extends Thread {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
        ) {
            // Step 6: Prompt client for file name
            writer.write("Enter the file name you want to download:\n");
            writer.flush();

            // Step 7: Read file name
            String fileName = reader.readLine();
            System.out.println("Client requested: " + fileName);

            File file = new File(fileName);

            // Step 8: Check if file exists
            if (file.exists() && file.isFile()) {
                writer.write("FOUND\n");
                writer.flush();

                // Send file size
                long fileSize = file.length();
                dataOut.writeLong(fileSize);

                // Send file content
                FileInputStream fileIn = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }

                fileIn.close();
                System.out.println("File " + fileName + " sent successfully.");
            } else {
                writer.write("NOT_FOUND\n");
                writer.flush();
                System.out.println("File not found: " + fileName);
            }

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
