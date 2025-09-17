
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class BankClient {
    private static final String SERVER_IP = "10.33.3.27"; 
    private static final int SERVER_PORT = 4560;         

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connected to BankServer.");

            System.out.print("Enter card number: ");
            String card = sc.nextLine().trim();

            System.out.print("Enter PIN: ");
            String pin = sc.nextLine().trim();

            out.writeUTF("AUTH:" + card + ":" + pin);
            out.flush();

            String authResponse = in.readUTF();
            if (!authResponse.equals("AUTH_OK")) {
                System.out.println("Authentication failed!");
                return;
            }
            System.out.println("Authentication successful!");

            out.writeUTF("BALANCE:" + card);
            out.flush();
            String balResp = in.readUTF();
            System.out.println("Current Balance: " + balResp.split(":")[1] + " USD");

            System.out.print("Enter amount to withdraw: ");
            String amount = sc.nextLine().trim();

            out.writeUTF("WITHDRAW:" + card + ":" + amount);
            out.flush();

            String withdrawResp = in.readUTF();
            System.out.println("Withdraw response: " + withdrawResp);

            // 4. Try to withdraw again (will be blocked by server)
            System.out.print("Try to withdraw again (same session)? Enter amount: ");
            String amount2 = sc.nextLine().trim();

            out.writeUTF("WITHDRAW:" + card + ":" + amount2);
            out.flush();

            String withdrawResp2 = in.readUTF();
            System.out.println("Second withdraw response: " + withdrawResp2);

            // 5. Optionally check balance again
            out.writeUTF("BALANCE:" + card);
            out.flush();
            String balResp2 = in.readUTF();
            System.out.println("Balance after withdraw: " + balResp2.split(":")[1] + " USD");

            System.out.println("Session ended.");

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}

