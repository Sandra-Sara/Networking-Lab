import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_IP = "10.33.2.220";
    private static final int SERVER_PORT = 5004;

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to server.");


            System.out.println("Server: " + serverReader.readLine());

            while (true) {
                System.out.print("Enter command (list, request <filename>, quit): ");
                String command = scanner.nextLine();
                serverWriter.write(command);
                serverWriter.newLine();
                serverWriter.flush();

                if (command.equalsIgnoreCase("quit")) {
                    System.out.println("Server: " + serverReader.readLine());
                    break;
                } else if (command.equalsIgnoreCase("list")) {
                    String response;
                    while ((response = serverReader.readLine()) != null && !response.isEmpty()) {
                        System.out.println("File: " + response);
                        if (!serverReader.ready()) break;
                    }
                } else if (command.startsWith("request ")) {
                    String response = serverReader.readLine();
                    if ("FOUND".equals(response)) {
                        long fileSize = dataInputStream.readLong();
                        String fileName = command.substring(8).trim();
                        File downloadDir = new File("client_files");
                        if (!downloadDir.exists()) downloadDir.mkdir();

                        FileOutputStream fos = new FileOutputStream(new File(downloadDir, fileName));
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalRead = 0;

                        while (totalRead < fileSize && (bytesRead = dataInputStream.read(buffer, 0,
                                (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                        fos.close();
                        System.out.println("File downloaded to client_files/" + fileName);
                    } else if ("NOT_FOUND".equals(response)) {
                        System.out.println("Server: File not found.");
                    } else {
                        System.out.println("Unexpected server response.");
                    }
                } else {
                    System.out.println("Server: " + serverReader.readLine());
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}
