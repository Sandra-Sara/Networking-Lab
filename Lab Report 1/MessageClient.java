
import java.io.*;
import java.net.*;

public class MessageClient {

    public static void main(String[] args) {
        String serverHost = "127.0.0.1";
        int serverPort = 12345;

        try {
            Socket conn = new Socket(serverHost, serverPort);
            System.out.println("Connected to server.");
            System.out.println("Type 'exit' to leave.");

            new Thread(new ServerReceiver(conn)).start();

            PrintWriter out = new PrintWriter(conn.getOutputStream(), true);
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            String msg;
            while (true) {
                msg = userInput.readLine();
                out.println(msg);
                if (msg.equalsIgnoreCase("exit")) {
                    break;
                }
            }

            conn.close();

        } catch (IOException e) {
            System.out.println("Cannot connect: " + e.getMessage());
        }

        System.out.println("Disconnected from server.");
    }

    private static class ServerReceiver implements Runnable {

        private Socket sock;

        public ServerReceiver(Socket socket) {
            this.sock = socket;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(sock.getInputStream())
                );
                String serverMsg;
                while ((serverMsg = in.readLine()) != null) {
                    System.out.println("[Server] " + serverMsg);
                }
            } catch (IOException e) {
                System.out.println("Lost connection to server.");
            }
        }
    }
}
