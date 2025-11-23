import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    static List<DataPoint> tahoeData = new ArrayList<>();
    static List<DataPoint> renoData = new ArrayList<>();
    
    static class DataPoint {
        int round;
        int cwnd;
        int ssthresh;
        
        DataPoint(int round, int cwnd, int ssthresh) {
            this.round = round;
            this.cwnd = cwnd;
            this.ssthresh = ssthresh;
        }
    }
    
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5000;
        int timeoutMs = 2000;

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(timeoutMs);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected to Server... Waiting for READY signal...");

            String ready = in.readLine();
            if (!"READY".equalsIgnoreCase(ready)) {
                System.out.println("Server not ready. Exiting...");
                return;
            }

            Scanner sc = new Scanner(System.in);
            System.out.print("Enter TCP mode (TAHOE / RENO): ");
            String mode = sc.nextLine().trim().toUpperCase();
            sc.close();
            if (!mode.equals("TAHOE") && !mode.equals("RENO")) {
                System.out.println("Invalid mode! Defaulting to TAHOE.");
                mode = "TAHOE";
            }
            System.out.println("== TCP " + mode + " Mode ==\n");

            int cwnd = 1;
            int ssthresh = 8;
            int dupACKcount = 0;
            String lastACK = "";
            int nextPacketNum = 1;
            int round = 1;
            int totalRounds = 20;
            Queue<String> packetsToRetransmit = new LinkedList<>();
            boolean inFastRecovery = false;
            
            Map<String, Long> sentPackets = new HashMap<>();


            while (round <= totalRounds) {
                System.out.println("Round " + round + ": cwnd = " + cwnd + ", ssthresh = " + ssthresh);
                
                if (mode.equals("TAHOE")) {
                    tahoeData.add(new DataPoint(round, cwnd, ssthresh));
                } else {
                    renoData.add(new DataPoint(round, cwnd, ssthresh));
                }

                List<String> packetsToSend = new ArrayList<>();

                while (!packetsToRetransmit.isEmpty() && packetsToSend.size() < cwnd) {
                    packetsToSend.add(packetsToRetransmit.poll());
                }

                while (packetsToSend.size() < cwnd && nextPacketNum <= totalRounds * 3) {
                    packetsToSend.add("pkt" + nextPacketNum);
                    nextPacketNum++;
                }

                if (packetsToSend.isEmpty()) {
                    break;
                }

                String packetMessage = String.join(",", packetsToSend);
                long sendTime = System.currentTimeMillis();
                out.println(packetMessage);
                System.out.println("Sent packets: " + String.join(", ", packetsToSend));
                
                for (String pkt : packetsToSend) {
                    sentPackets.put(pkt, sendTime);
                }

                dupACKcount = 0;
                lastACK = "";
                boolean fastRetransmitTriggered = false;
                boolean timeoutTriggered = false;
                Set<String> ackedPackets = new HashSet<>();

                int maxACKs = packetsToSend.size() + 2;
                int acksReceived = 0;
                long roundStartTime = System.currentTimeMillis();

                while (acksReceived < maxACKs) {
                    String ack = null;
                    try {
                        ack = in.readLine();
                        if (ack == null) {
                            break;
                        }
                    } catch (SocketTimeoutException e) {
                        
                        System.out.println("==> Timeout detected: No ACK received within " + timeoutMs + "ms");
                        timeoutTriggered = true;
                        
                       
                        ssthresh = Math.max(cwnd / 2, 1);
                        
                        
                        cwnd = 1;
                        inFastRecovery = false;
                        System.out.println("Timeout: cwnd -> 1, ssthresh -> " + ssthresh);
                       
                        for (String pkt : packetsToSend) {
                            if (!ackedPackets.contains(pkt) && !packetsToRetransmit.contains(pkt)) {
                                packetsToRetransmit.offer(pkt);
                            }
                        }
                        
        
                        if (mode.equals("TAHOE")) {
                            tahoeData.add(new DataPoint(round, cwnd, ssthresh));
                        } else {
                            renoData.add(new DataPoint(round, cwnd, ssthresh));
                        }
                        break;
                    }
                    
                    if (ack == null) {
                        break;
                    }

                    acksReceived++;
                    System.out.println("Received: " + ack);
                    System.out.flush(); 

                    if (ack.startsWith("ACK:")) {
                        String ackPkt = ack.substring(4);

                       
                        if (!ackPkt.equals("NA") && !ackedPackets.contains(ackPkt)) {
                           
                            for (String pkt : packetsToSend) {
                                if (ackPkt.equals(pkt)) {
                                    ackedPackets.add(ackPkt);
                                    sentPackets.remove(pkt); 
                                    break;
                                }
                            }
                        }

                       
                        if (!lastACK.isEmpty() && lastACK.equals(ack)) {
                            dupACKcount++;

                            if (dupACKcount == 3 && !fastRetransmitTriggered && !timeoutTriggered) {
                               
                                System.out.println("==> 3 Duplicate ACKs: Fast Retransmit triggered.");
                                System.out.flush();
                                fastRetransmitTriggered = true;

                                
                                String lostPacket = null;

                                if (ackPkt.equals("NA") || ackPkt.equals("pkt0")) {
                                    
                                    lostPacket = packetsToSend.get(0);
                                } else {
                                   
                                    int ackNum = extractPacketNumber(ackPkt);
                                    lostPacket = "pkt" + (ackNum + 1);

                                   
                                    boolean found = false;
                                    for (String pkt : packetsToSend) {
                                        if (pkt.equals(lostPacket)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                   
                                    if (!found) {
                                        for (String pkt : packetsToSend) {
                                            int pktNum = extractPacketNumber(pkt);
                                            if (pktNum > ackNum) {
                                                lostPacket = pkt;
                                                break;
                                            }
                                        }
                                    }
                                }

                               
                                if (lostPacket != null) {
                                    if (mode.equals("TAHOE")) {
                                        
                                        boolean addRemaining = false;
                                        for (String pkt : packetsToSend) {
                                            if (pkt.equals(lostPacket)) {
                                                addRemaining = true;
                                            }
                                            if (addRemaining && !packetsToRetransmit.contains(pkt)) {
                                                packetsToRetransmit.offer(pkt);
                                            }
                                        }
                                    } else {
                                        
                                        boolean addRemaining = false;
                                        for (String pkt : packetsToSend) {
                                            if (pkt.equals(lostPacket)) {
                                                addRemaining = true;
                                            }
                                            if (addRemaining && !packetsToRetransmit.contains(pkt)) {
                                                packetsToRetransmit.offer(pkt);
                                            }
                                        }
                                    }
                                }

                                
                                ssthresh = Math.max(cwnd / 2, 1);

                                if (mode.equals("RENO")) {
                                    
                                    cwnd = ssthresh;
                                    inFastRecovery = true; 
                                    System.out.println(
                                            "TCP RENO Fast Recovery: cwnd -> " + cwnd + ", ssthresh -> " + ssthresh);
                                   
                                    renoData.add(new DataPoint(round, cwnd, ssthresh));
                                } else {
                                    
                                    cwnd = 1;
                                    inFastRecovery = false; 
                                    System.out.println("TCP TAHOE Reset: cwnd -> 1");
                                 
                                    tahoeData.add(new DataPoint(round, cwnd, ssthresh));
                                }

            
                                dupACKcount = 0;
                                lastACK = "";

                                
                            }
                        } else {
                           
                            dupACKcount = 1;
                            lastACK = ack;
                        }
                    }

                    
                    if (fastRetransmitTriggered) {
                        break;
                    }

                    
                    if (!fastRetransmitTriggered && ackedPackets.size() == packetsToSend.size()) {
                        
                        break;
                    }
                }

                
                if (timeoutTriggered) {
                   
                } else if (mode.equals("RENO") && inFastRecovery && !fastRetransmitTriggered) {
                    
                    cwnd = cwnd + 1;
                    System.out.println("Congestion Avoidance (Fast Recovery): cwnd -> " + cwnd);
                    
                    inFastRecovery = false;
                } else if (!fastRetransmitTriggered) {
                    
                    if (cwnd < ssthresh) {
                       
                        cwnd = cwnd * 2;
                        System.out.println("Slow Start: cwnd -> " + cwnd);
                    } else {
                        
                        cwnd = cwnd + 1;
                        System.out.println("Congestion Avoidance: cwnd -> " + cwnd);
                    }
                } else {
                    
                }

                
                round++;
                System.out.println();
                Thread.sleep(500);
            }

            
            System.out.println("\nTransmission complete. Disconnecting...");
            
            
            saveDataToFile(mode);
            
            socket.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void saveDataToFile(String mode) {
        try {
            String filename = mode.toLowerCase() + "_data.csv";
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("Round,cwnd,ssthresh");
            
            List<DataPoint> data = mode.equals("TAHOE") ? tahoeData : renoData;
            for (DataPoint point : data) {
                writer.println(point.round + "," + point.cwnd + "," + point.ssthresh);
            }
            
            writer.close();
            System.out.println("Data saved to " + filename);
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
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
