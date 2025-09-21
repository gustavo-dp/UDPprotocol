import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Cliente {
    public static void main(String[] args)  {
        try (DatagramSocket socket = new DatagramSocket()) {
            String sendMessage = new  String("YOOOOOO");
            ByteBuffer sendBuffer = ByteBuffer.wrap(sendMessage.getBytes());
            int port = 8080;

            InetAddress serverAddress = InetAddress.getByName("localhost");
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer.array(), sendBuffer.array().length, serverAddress, port);

            socket.send(sendPacket);

            ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer.array(), receiveBuffer.array().length);

            socket.receive(receivePacket);
            String receiveText = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println(new String(receivePacket.getData(), 0, receivePacket.getLength()));

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
