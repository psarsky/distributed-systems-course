import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int MULTICAST_PORT = 12346;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                DatagramSocket udpSocket = new DatagramSocket();
                MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in);
        ) {
            // joining multicast group
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            multicastSocket.joinGroup(
                    new InetSocketAddress(group, MULTICAST_PORT),
                    NetworkInterface.getByInetAddress(InetAddress.getLocalHost())
            );

            // nickname
            System.out.print(in.readLine() + " ");
            String nickname = scanner.nextLine();
            out.println(nickname);
            System.out.print("You: ");

            // receive threads
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.equals("Server is shutting down")) {
                            System.out.println("\r" + message);
                            System.exit(0);
                        }
                        System.out.println("\r" + message);
                        System.out.print("You: ");
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("\rDisconnected from server");
                    }
                    System.exit(0);
                }
            }).start();
            receiveUDP(udpSocket, "UDP");
            receiveUDP(multicastSocket, "MULTICAST");

            // send loop
            while (scanner.hasNextLine()) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("quit")) {
                    running = false;
                    System.out.println("Exiting...");
                    break;
                }

                if (message.equals("U") || message.equals("M")) {
                    boolean udp = message.equals("U");
                    byte[] data = (udp
                            ? """
                              \s
                               /\\_/\\
                              ( o.o )
                               > ^ <\
                              """
                            : """
                              \s
                              (-_-)\
                              """).getBytes();
                    DatagramSocket socketToUse = udp ? udpSocket : multicastSocket;
                    InetAddress address = udp ? InetAddress.getByName(SERVER_ADDRESS) : group;
                    int port = udp ? SERVER_PORT : MULTICAST_PORT;
                    socketToUse.send(new DatagramPacket(data, data.length, address, port));
                } else {
                    out.println(message);
                }
                System.out.print("You: ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveUDP(DatagramSocket socket, String prefix) {
        new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    System.out.println("\r[" + prefix + "]: " + message);
                    System.out.print("You: ");
                } catch (IOException e) {
                    break;
                }
            }
        }).start();
    }
}
