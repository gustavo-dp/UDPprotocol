import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

public class ClientHandler implements Runnable {

    private static final int PAYLOAD = 1024;
    private static final int HEADER_LENGTH = 4 + 4 + 8 + 1;

    private final DatagramSocket socket;
    private final InetAddress clientAddress;
    private final int clientPort;
    private final String requestedFile;
    private final Map<Integer, byte[]> cachePacotes = new HashMap<>();

    public ClientHandler(DatagramSocket socket, DatagramPacket initialPacket) {
        this.socket = socket;
        this.clientAddress = initialPacket.getAddress();
        this.clientPort = initialPacket.getPort();
        String request = new String(initialPacket.getData(), 0, initialPacket.getLength());
        System.out.println("Handler criado para " + clientAddress + ":" + clientPort + " -> " + request);
        this.requestedFile = request.startsWith("GET /") ? request.substring(5).trim() : null;
    }

    @Override
    public void run() {
        System.out.println("Diretório atual do servidor: " + new java.io.File(".").getAbsolutePath());

        if (requestedFile == null) return;

        try {
            File archive = new File(requestedFile);
            if (archive.exists() && !archive.isDirectory()) {
                sendArchive(archive);
                listenForNacks();
            } else {
                sendErrorMessage("ARQUIVO_NAO_ENCONTRADO");
            }
        } catch (IOException e) {
            System.err.println("Erro no handler do cliente " + clientPort + ": " + e.getMessage());
        }
    }

    private void sendArchive(File archive) throws IOException {
        try (FileInputStream fis = new FileInputStream(archive)) {
            byte[] bufferData = new byte[PAYLOAD];
            int sequenceNumber = 0;
            int bytesRead;

            while ((bytesRead = fis.read(bufferData)) != -1) {
                byte[] packetData = buildPacket(sequenceNumber, bytesRead, bufferData, fis.available() == 0);
                DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, clientAddress, clientPort);
                socket.send(sendPacket);
                cachePacotes.put(sequenceNumber, packetData);
                sequenceNumber++;
                try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            System.out.println("Transferência inicial para " + clientPort + " concluída com " + cachePacotes.size() + " pacotes.");
        }
    }

    private void listenForNacks() throws IOException {
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        try {
            socket.setSoTimeout(5000);
            while (true) {
                socket.receive(receivePacket);
                if (receivePacket.getAddress().equals(clientAddress) && receivePacket.getPort() == clientPort) {
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    if (message.startsWith("NACK:")) {
                        System.out.println("Recebido NACK do cliente " + clientPort + " para pacotes: " + message.substring(5));
                        String[] parts = message.substring(5).split(",");
                        for (String seqNumStr : parts) {
                            try {
                                int seqNum = Integer.parseInt(seqNumStr.trim());
                                byte[] packetToResend = cachePacotes.get(seqNum);
                                if (packetToResend != null) {
                                    DatagramPacket resendPacket = new DatagramPacket(packetToResend, packetToResend.length, clientAddress, clientPort);
                                    socket.send(resendPacket);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Nenhum NACK recebido de " + clientPort + " por 5s. Concluindo transação.");
        } finally {
            cachePacotes.clear();
        }
    }

    private byte[] buildPacket(int sequenceNumber, int bytesRead, byte[] bufferData, boolean isLast) {
        CRC32 crc32 = new CRC32();
        crc32.update(bufferData, 0, bytesRead);
        long checksum = crc32.getValue();
        ByteBuffer packetBuffer = ByteBuffer.allocate(HEADER_LENGTH + bytesRead);
        packetBuffer.putInt(sequenceNumber);
        packetBuffer.putInt(bytesRead);
        packetBuffer.putLong(checksum);
        packetBuffer.put(isLast ? (byte) 1 : (byte) 0);
        packetBuffer.put(bufferData, 0, bytesRead);
        return packetBuffer.array();
    }

    private void sendErrorMessage(String error) throws IOException {
        String message = "ERROR: " + error;
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress, clientPort);
        socket.send(sendPacket);
    }
}