import java.io.*;
import java.net.*;

public class Server {

    public static void main(String[] args) {
        final int PORT = 5004;

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}

class ClientHandler extends Thread {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            clientWriter.write("Connected to the file server.");
            clientWriter.newLine();
            clientWriter.flush();

            String command;
            while ((command = clientReader.readLine()) != null) {
                if (command.equalsIgnoreCase("quit")) {
                    clientWriter.write("Goodbye!");
                    clientWriter.newLine();
                    clientWriter.flush();
                    break;
                } else if (command.equalsIgnoreCase("list")) {
                    File dir = new File("server_files/");
                    File[] files = dir.listFiles();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (file.isFile()) {
                                clientWriter.write(file.getName());
                                clientWriter.newLine();
                            }
                        }
                    } else {
                        clientWriter.write("No files found.");
                        clientWriter.newLine();
                    }
                    clientWriter.flush();
                } else if (command.startsWith("request ")) {
                    String fileName = command.substring(8)
                    .trim();
                    File file = new File("server_files/" + fileName);
                    if (file.exists() && file.isFile()) {
                        clientWriter.write("FOUND");
                        clientWriter.newLine();
                        clientWriter.flush();

                        long fileSize = file.length();
                        dos.writeLong(fileSize);

                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                dos.write(buffer, 0, bytesRead);
                            }
                            dos.flush();
                        }

                        System.out.println("File sent: " + fileName);
                    } else {
                        clientWriter.write("NOT_FOUND");
                        clientWriter.newLine();
                        clientWriter.flush();
                        System.out.println("File not found: " + fileName);
                    }
                } else {
                    clientWriter.write("Invalid command. Use: list, request <filename>, or quit.");
                    clientWriter.newLine();
                    clientWriter.flush();
                }
            }

        } catch (IOException e) {
            System.out.println("ClientHandler error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client connection closed.");
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
