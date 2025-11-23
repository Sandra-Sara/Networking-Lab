import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    public static void main(String[] args) {
        int port = 5000;
        System.out.println("Server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
          
            Socket socket = serverSocket.accept();
            System.out.println("Client connected: " + socket.getInetAddress());

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("READY");

            Random rand = new Random();
            String lastSuccessfullyReceivedPacketAcrossRounds = ""; 
            int roundCount = 0;

            
            while (true) {
              
                String message = in.readLine();
                if (message == null || message.isEmpty()) {
                    break;
                }

                roundCount++;

                
                String[] packets = message.split(",");
                List<String> packetList = new ArrayList<>();
                for (String pkt : packets) {
                    packetList.add(pkt.trim());
                }

                if (packetList.isEmpty()) {
                    continue;
                }
                
                
                boolean simulateTimeout = false;
                int lossIndex = -1;
                
               
                boolean canSimulateCongestion = false;
                for (String pkt : packetList) {
                    int pktNum = extractPacketNumber(pkt);
                    if (pktNum >= 11) {
                        canSimulateCongestion = true;
                        break;
                    }
                }
                
                if (canSimulateCongestion && roundCount > 3) {
                   
                    int randomValue = rand.nextInt(100);
                    
                    if (randomValue < 5) {
                        
                        simulateTimeout = true;
                        System.out.println("Simulating timeout: Not sending ACKs for this round (packets: " + 
                                          String.join(", ", packetList) + ")");
                        
                        continue;
                    } else if (randomValue < 25) {
                       
                        List<Integer> eligibleIndices = new ArrayList<>();
                        for (int i = 0; i < packetList.size(); i++) {
                            int pktNum = extractPacketNumber(packetList.get(i));
                            if (pktNum >= 11) {
                                eligibleIndices.add(i);
                            }
                        }
                        if (!eligibleIndices.isEmpty()) {
                            lossIndex = eligibleIndices.get(rand.nextInt(eligibleIndices.size()));
                            System.out.println("Simulating packet loss: Packet " + packetList.get(lossIndex) + 
                                              " will be lost (will trigger duplicate ACKs)");
                        }
                    }
                   
                }

                
                String lastSuccessfullyReceivedPacket = lastSuccessfullyReceivedPacketAcrossRounds;

                for (int i = 0; i < packetList.size(); i++) {
                    String packet = packetList.get(i);

                    if (i == lossIndex) {
                        
                        if (i == 0 && lastSuccessfullyReceivedPacket.isEmpty()) {
                            
                            String ackToSend = "ACK:NA";
                            System.out.println("Packet lost: " + packet + " - Sending duplicate ACK:pkt0 (3 times)");
                            out.println(ackToSend);
                            out.println(ackToSend);
                            out.println(ackToSend);
                        } else {
                            
                            String ackToSend = lastSuccessfullyReceivedPacket.isEmpty() ? "ACK:pkt0"
                                    : "ACK:" + lastSuccessfullyReceivedPacket;
                            System.out.println("Packet lost: " + packet + " - Sending duplicate ACK:"
                                    + lastSuccessfullyReceivedPacket
                                    + " (3 times)");
                            out.println(ackToSend);
                            out.println(ackToSend);
                            out.println(ackToSend);
                        }
                       
                        break;
                    } else {
                        
                        System.out.println("Received: " + packet + " - Sending ACK:" + packet);
                        out.println("ACK:" + packet);
                        lastSuccessfullyReceivedPacket = packet;
                        lastSuccessfullyReceivedPacketAcrossRounds = packet; 
                    }
                }

                
                Thread.sleep(100);
            }

            
            System.out.println("\nClient disconnected.");
            socket.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int extractPacketNumber(String packet) {
        try {
            if (packet.startsWith("pkt")) {
                return Integer.parseInt(packet.substring(3));
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }
}
