import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.CRC32;

public class ClienteErro {
    private static final int PAYLOAD = 1024;
    private static final int HEADER = 4 + 4 + 8 + 1;
    private static final int PACKAGE_LENGTH = HEADER + PAYLOAD;
    private static final double CHANCE_TO_LOSE = 0.1;
    private static final Random random = new Random();
    public static void main(String[] args)  {
        String archiveName = "/home/deda/College/class_redes_de_computadores/firstProject_udp/teste.txt";
        String archiveSaved = "recebido_" + new File(archiveName).getName();

        try(DatagramSocket socket = new DatagramSocket()) {
            InetAddress inet = InetAddress.getByName("localhost");
            int port = 8080;
            String request = "GET /" + archiveName;
            byte[] sendBuffer = request.getBytes();
            DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, inet, port);
            socket.send(packet);

            TreeMap<Integer, byte[]> segmentsReceived = new TreeMap<>();
            int lastNumberByte = -1;

            socket.setSoTimeout(2000);

            System.out.println("Aguardando pacotes do servidor...");
            while (true) {
                try {

                    byte[] receiveBuffer = new byte[PACKAGE_LENGTH];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(receivePacket);

                    ByteBuffer receiveBufferPacket = ByteBuffer.wrap(receivePacket.getData());
                    int sequenceNumber = receiveBufferPacket.getInt();
                    int dataLength = receiveBufferPacket.getInt();
                    long checksum = receiveBufferPacket.getLong();
                    byte flag = receiveBufferPacket.get();


                    byte[] data = new byte[dataLength];
                    receiveBufferPacket.get(data);

                    CRC32 crc32 = new CRC32();
                    crc32.update(data, 0, dataLength);
                    long checksumValue = crc32.getValue();

                    if (checksumValue == checksum) {
                        segmentsReceived.put(sequenceNumber, data);
                        System.out.println("Pacote #" + sequenceNumber + " recebido e validado.");

                        if (flag == 1) {
                            lastNumberByte = sequenceNumber;
                        }
                    } else {
                        System.out.println("Pacote #" + sequenceNumber + " corrompido e descartado!");
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout! O servidor provavelmente terminou de enviar.");
                    break;
                }
            }
            if(lastNumberByte != -1 && segmentsReceived.size() == lastNumberByte + 1){
                System.out.println("Todos os pacotes recebidos com sucesso! Montando o arquivo...");
                try(FileOutputStream fos = new FileOutputStream(archiveSaved)) {
                    for(byte[] segment : segmentsReceived.values()){
                        fos.write(segment);
                    }
                    System.out.println("Arquivo '" + archiveSaved + "' salvo com sucesso!");
                }
            }else{
                System.out.println("Falha na transferência: Pacotes faltando ou corrompidos.");
                System.out.println("Recebidos " + segmentsReceived.size() + " pacotes, mas o último era #" + lastNumberByte);

            }
        }catch (IOException e) {
            e.printStackTrace();
        }


    }
}
