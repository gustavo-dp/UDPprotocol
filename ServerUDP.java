// Salve este arquivo como ServidorOlaMundo.java
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ServerUDP {

    public static void main(String[] args) {

        try(DatagramSocket serverSocket = new DatagramSocket(8080)) {
            while(true) {

                byte[] buffer = new byte[1024]; // array simples
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(receivePacket);


                System.out.println(new String(receivePacket.getData(), 0, receivePacket.getLength()));

                // resposta
                String sendText = "OIIIIIIIIIII";
                byte[] sendBytes = sendText.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(sendBytes, sendBytes.length,
                        receivePacket.getAddress(),
                        receivePacket.getPort());
                serverSocket.send(sendPacket);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}