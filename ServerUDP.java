import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerUDP {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            System.out.println("Servidor UDP multithread iniciado na porta " + PORT);
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(receivePacket);

                    ClientHandler handler = new ClientHandler(serverSocket, receivePacket);
                    new Thread(handler).start();

                } catch (Exception e) {
                    System.err.println("Erro ao aceitar nova conexão: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}