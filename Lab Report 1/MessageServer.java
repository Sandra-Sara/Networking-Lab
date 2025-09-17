import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MessageServer {

    private static Map<Integer, PrintWriter> connectedClients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int listenPort = 12345;
        System.out.println("Server started on port " + listenPort);
        System.out.println("Enter 'exit' to terminate the server.");

        new Thread(new ConsoleHandler()).start();

        ServerSocket serverSock = new ServerSocket(listenPort);
        while (true) {
            Socket clientSock = serverSock.accept();
            new Thread(new ClientProcessor(clientSock)).start();
        }
    }

    private static class ClientProcessor implements Runnable {
        private Socket socket;
        private int portNumber;
        private PrintWriter writer;

        public ClientProcessor(Socket sock) {
            this.socket = sock;
            this.portNumber = sock.getPort();
        }

        public void run() {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
                );
                this.writer = new PrintWriter(socket.getOutputStream(), true);

                connectedClients.put(portNumber, writer);
                System.out.println("Connected: " + portNumber);

                writer.println("Connected successfully. Your port: " + portNumber);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equalsIgnoreCase("exit")) break;
                    System.out.println("Client " + portNumber + ": " + line);
                    writer.println("Server ack: " + line);
                }

            } catch (IOException e) {
                System.out.println("Error with client " + portNumber);
            } finally {
                connectedClients.remove(portNumber);
                System.out.println("Disconnected: " + portNumber);
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }

    private static class ConsoleHandler implements Runnable {
        public void run() {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if (command.equalsIgnoreCase("exit")) {
                        System.out.println("Shutting down server...");
                        for (PrintWriter w : connectedClients.values()) {
                            w.println("Server is stopping.");
                        }
                        System.exit(0);
                    }

                    String[] tokens = command.split(":", 2);
                    if (tokens.length == 2) {
                        try {
                            int target = Integer.parseInt(tokens[0].trim());
                            String msg = tokens[1].trim();
                            PrintWriter client = connectedClients.get(target);
                            if (client != null) {
                                client.println("Server message: " + msg);
                                System.out.println("Sent to " + target);
                            } else {
                                System.out.println("Client not found: " + target);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid port format.");
                        }
                    } else {
                        System.out.println("Format: port: message");
                    }
                }
            } catch (IOException e) {
                System.out.println("Console read failed.");
            }
        }
    }
}
