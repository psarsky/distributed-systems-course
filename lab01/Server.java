import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private static final int PORT = 12345;
    private static DatagramSocket udpSocket;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final Set<SocketAddress> udpClients = ConcurrentHashMap.newKeySet();
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        // UDP receive+broadcast thread
        startUDP();

        // exit command monitoring thread
        startExitMonitoring();

        // client connection loop
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is up");
            while (true) {
                ClientHandler handler = new ClientHandler(serverSocket.accept());
                clients.add(handler);
                pool.execute(handler);
                System.out.println("New client connected");
            }
        }
    }

    private static void startUDP() throws SocketException {
        udpSocket = new DatagramSocket(PORT);

        new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    SocketAddress sender = packet.getSocketAddress();

                    udpClients.add(sender);
                    broadcastUDP(message, sender);

                    System.out.println("[UDP]: " + message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void startExitMonitoring() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    System.out.println("Shutting down server...");
                    for (ClientHandler client : clients) {
                        client.sendMessage("Server is shutting down");
                        clients.remove(client);
                        try {
                            client.socket.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    System.exit(0);
                }
            }
        }).start();
    }

    private static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    private static void broadcastUDP(String message, SocketAddress sender) throws IOException {
        byte[] data = message.getBytes();
        for (SocketAddress client : udpClients) {
            if (!client.equals(sender)) {
                try {
                    DatagramPacket packet = new DatagramPacket(data, data.length, client);
                    udpSocket.send(packet);
                } catch (IOException e) {
                    udpClients.remove(client);
                }
            }
        }
    }

    private static void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    static class ClientHandler implements Runnable {

        private final Socket socket;
        private PrintWriter out;
        private String nickname;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                this.out = out;

                out.println("Nickname:");
                nickname = in.readLine();
                System.out.println(nickname + " joined the chat");
                Server.broadcast(nickname + " joined the chat", this);

                String message;
                while ((message = in.readLine()) != null) {
                    String fullMessage = nickname + ": " + message;
                    System.out.println(fullMessage);
                    Server.broadcast(fullMessage, this);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected");
            } finally {
                Server.removeClient(this);
                System.out.println(nickname + " left the chat");
                Server.broadcast(nickname + " left the chat", this);
            }
        }

        public synchronized void sendMessage(String message) {
            out.println(message);
        }
    }
}