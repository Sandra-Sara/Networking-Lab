import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class BankServer {
    private final int port;
    private final Map<String, Double> accounts = new ConcurrentHashMap<>(); // card -> balance in USD
    private final Map<String, String> completedTx = new ConcurrentHashMap<>(); // txid -> response
    private final File txLogFile = new File("completed_tx.log");
    private final File accountsFile = new File("accounts.state");

    public BankServer(int port) {
        this.port = port;
        loadState();
    }

    // Load previous accounts and transactions from files
    private synchronized void loadState() {
        // Load completed transactions
        if (txLogFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(txLogFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) completedTx.put(parts[0], parts[1]);
                }
            } catch (IOException e) {
                System.err.println("Failed to load tx log: " + e.getMessage());
            }
        }

        // Load accounts
        if (accountsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(accountsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) accounts.put(parts[0], Double.parseDouble(parts[1]));
                }
            } catch (IOException e) {
                System.err.println("Failed to load accounts: " + e.getMessage());
            }
        } else {
            // initialize demo accounts
            accounts.put("1111-2222-3333-4444", 100.0); // $100.0
            accounts.put("9999-8888-7777-6666", 50.0);  // $50.0
            persistAccounts();
        }
    }

    // Save completed transaction to file
    private synchronized void persistTx(String txid, String response) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(txLogFile, true))) {
            bw.write(txid + "|" + response);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Failed to persist tx: " + e.getMessage());
        }
        completedTx.put(txid, response);
    }

    // Save current account balances to file
    private synchronized void persistAccounts() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(accountsFile))) {
            for (Map.Entry<String, Double> e : accounts.entrySet()) {
                bw.write(e.getKey() + "|" + e.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to persist accounts: " + e.getMessage());
        }
    }

    // Start server and accept clients
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("BankServer listening on port " + port);

        while (true) {
            Socket client = serverSocket.accept();
            DataInputStream in = new DataInputStream(client.getInputStream());
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            new ClientHandler(client, in, out).start();
        }
    }

    // Handles each client in separate thread
    private class ClientHandler extends Thread {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private volatile boolean running = true;

        private boolean authenticated = false; // Log in status
        private boolean hasWithdrawn = false;  // Track if this session already withdrew

        ClientHandler(Socket s, DataInputStream in, DataOutputStream out) {
            this.socket = s;
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                while (running && !socket.isClosed()) {
                    String msg;
                    try {
                        msg = in.readUTF();
                    } catch (EOFException eof) {
                        break;
                    }
                    if (msg == null) break;

                    System.out.println("Received: " + msg);

                    if (msg.startsWith("AUTH:")) {
                        handleAuth(msg);
                    } else if (msg.startsWith("WITHDRAW:")) {
                        handleWithdraw(msg);
                    } else if (msg.startsWith("BALANCE:")) {
                        handleBalance(msg);
                    } else if (msg.startsWith("ACK:")) {
                        // ignore here; handled in sendResponseAndWaitAck
                    } else if (msg.equals("Exit")) {
                        running = false;
                    } else {
                        out.writeUTF("UNKNOWN_COMMAND");
                    }
                }
            } catch (IOException e) {
                System.err.println("Client handler IO error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // Handle authentication
        private void handleAuth(String msg) throws IOException {
            String[] parts = msg.split(":", 3);
            if (parts.length != 3) {
                out.writeUTF("AUTH_FAIL");
                return;
            }
            String card = parts[1].trim();
            String pin = parts[2].trim();
            if (accounts.containsKey(card) && correctPin(card).equals(pin)) {
                authenticated = true;
                out.writeUTF("AUTH_OK");
            } else {
                out.writeUTF("AUTH_FAIL");
            }
        }

        // Get correct PIN for card
        private String correctPin(String card) {
            if (card.equals("1111-2222-3333-4444")) return "1234";
            if (card.equals("9999-8888-7777-6666")) return "5678";
            return "0000";
        }

        // Handle withdraw request
        private void handleWithdraw(String msg) throws IOException {
            if (!authenticated) {
                out.writeUTF("WITHDRAW_FAIL:NOT_AUTHENTICATED");
                return;
            }

            if (hasWithdrawn) {
                out.writeUTF("WITHDRAW_FAIL:ALREADY_DONE");
                return;
            }

            String[] parts = msg.split(":", 3);
            if (parts.length != 3) {
                out.writeUTF("WITHDRAW_FAIL:BAD_FORMAT");
                return;
            }

            final String card = parts[1].trim();
            double amount;
            try {
                amount = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                out.writeUTF("WITHDRAW_FAIL:BAD_AMOUNT");
                return;
            }

            String response;
            synchronized (accounts) {
                if (!accounts.containsKey(card)) {
                    response = "WITHDRAW_FAIL:UNKNOWN_ACCOUNT";
                } else {
                    double balance = accounts.get(card);
                    if (balance >= amount) {
                        double newBal = balance - amount;
                        accounts.put(card, newBal);
                        persistAccounts();
                        response = "WITHDRAW_OK:" + newBal;
                        hasWithdrawn = true; // mark that this session withdrew
                    } else {
                        response = "INSUFFICIENT_FUNDS";
                    }
                }
            }

            out.writeUTF(response);
            out.flush();
            System.out.println("Withdraw response sent: " + response);
        }

        // Handle balance inquiry
        private void handleBalance(String msg) throws IOException {
            if (!authenticated) {
                out.writeUTF("BALANCE_FAIL:NOT_AUTHENTICATED");
                return;
            }

            String[] parts = msg.split(":", 2);
            if (parts.length != 2) {
                out.writeUTF("BALANCE_FAIL:BAD_FORMAT");
                return;
            }
            String card = parts[1].trim();
            synchronized (accounts) {
                if (!accounts.containsKey(card)) {
                    out.writeUTF("BALANCE_FAIL:UNKNOWN_ACCOUNT");
                } else {
                    double balance = accounts.get(card);
                    out.writeUTF("BALANCE_OK:" + balance);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 4560;
        BankServer server = new BankServer(port);
        server.start();
    }
}
