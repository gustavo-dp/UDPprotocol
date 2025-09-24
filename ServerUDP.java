// Salve este arquivo como ServidorOlaMundo.java
import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class ServerUDP {
    private static final int PORT = 8080;
    private static final int PAYLOAD = 1024;
    private static final int HEADER_LENGTH = 4 + 4 + 8 + 1;
    private static final int PACKET_LENGTH = HEADER_LENGTH + PAYLOAD;
    public static void main(String[] args) {

        try(DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            while(true) {

                byte[] buffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(receivePacket);

                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress address = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                System.out.println("Requisição recebida de " + address + ":" + clientPort + " -> " + request);

               if (request.startsWith("GET /")){
                   String archiveName = request.substring(5);
                   File archive = new File(archiveName);
                   if(archive.exists() && !archive.isDirectory()) {
                       System.out.println("Arquivo encontrado: " + archiveName + ". Iniciando transferência.");
                       sendArchive(archive, address, clientPort, serverSocket);
                   }else{
                       System.out.println("Erro: Arquivo não encontrado - " + archiveName + ".");
                       sendErrorMessage("ARQUIVO_NAO_ENCONTRADO", address, clientPort, serverSocket);
                   }
               }

            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    private static void sendArchive(File archive, InetAddress address, int clientPort, DatagramSocket serverSocket) throws IOException {
        try (FileInputStream fis = new FileInputStream(archive)) {
            byte[] bufferData = new byte[PAYLOAD];
            int sequenceNumber = 0;
            int bytesRead;

            while ((bytesRead = fis.read(bufferData)) != -1) {
                CRC32 crc32 = new CRC32();
                crc32.update(bufferData, 0, bytesRead);
                long checksum = crc32.getValue();


                ByteBuffer packetBuffer = ByteBuffer.allocate(HEADER_LENGTH + bytesRead);
                packetBuffer.putInt(sequenceNumber);
                packetBuffer.putInt(bytesRead);
                packetBuffer.putLong(checksum);

                boolean lastPacket = (fis.available() == 0);
                packetBuffer.put(lastPacket ? (byte) 1 : (byte) 0);

                packetBuffer.put(bufferData, 0, bytesRead);

                byte[] UDPpackage = packetBuffer.array();
                DatagramPacket sendPacket = new DatagramPacket(UDPpackage, UDPpackage.length, address, clientPort);
                serverSocket.send(sendPacket);

                System.out.println("Enviado pacote #" + sequenceNumber + " com " + bytesRead + " bytes de dados.");
                sequenceNumber++;

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Transferência do arquivo " + archive.getName() + " concluída.");
        }
    }
    private static void sendErrorMessage(String error, InetAddress clientAddress, int clientPort, DatagramSocket socket) throws IOException {
        String mensage = "ERROR: " + error;
        byte[] sendBuffer = mensage.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress, clientPort);
        socket.send(sendPacket);
    }
}
