import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

public class Cliente_2 {

    private static final int PAYLOAD = 1024;
    private static final int HEADER_LENGTH = 4 + 4 + 8 + 1;
    private static final int PACKAGE_LENGTH = HEADER_LENGTH + PAYLOAD;
    private static final double CHANCE_DE_PERDA = 0.1;
    private static final Random random = new Random();

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Digite o IP do servidor (ou 'localhost'): ");
            String serverIp = scanner.nextLine();
            System.out.print("Digite a porta do servidor (ex: 8080): ");
            int port = scanner.nextInt();
            scanner.nextLine();
            System.out.print("Deseja simular a perda de pacotes? (s/n): ");
            String respostaSimulacao = scanner.nextLine();
            boolean simularPerda = respostaSimulacao.trim().toLowerCase().startsWith("s");
            String archiveName = "apostila.pdf";
            String archiveSaved = "recebido_" + new File(archiveName).getName();

            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress inet = InetAddress.getByName(serverIp);
                String request = "GET /" + (archiveName.startsWith("/") ? archiveName.substring(1) : archiveName);
                byte[] sendBuffer = request.getBytes();
                DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, inet, port);
                socket.send(packet);
                System.out.println("Requisição enviada: " + request);
                if (simularPerda) System.out.println("--- SIMULAÇÃO DE PERDA DE PACOTES ATIVADA ---");

                TreeMap<Integer, byte[]> segmentsReceived = new TreeMap<>();
                int lastNumberByte = -1;
                boolean transferenciaCompleta = false;
                boolean erroServidor = false;
                int tentativas = 0;
                final int MAX_TENTATIVAS = 10;

                while (!transferenciaCompleta && !erroServidor && tentativas < MAX_TENTATIVAS) {
                    System.out.println("\n--- Tentativa " + (tentativas + 1) + " de recepção ---");
                    socket.setSoTimeout(2000);
                    while (true) {
                        try {
                            byte[] receiveBuffer = new byte[PACKAGE_LENGTH];
                            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                            socket.receive(receivePacket);


                            String possibleError = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            if (possibleError.startsWith("ERROR:")) {
                                System.err.println("Recebida mensagem de erro do servidor: " + possibleError);
                                erroServidor = true;
                            }

                            if (simularPerda && random.nextDouble() < CHANCE_DE_PERDA) {
                                ByteBuffer tempBuffer = ByteBuffer.wrap(receivePacket.getData());
                                int seqNumPreview = tempBuffer.getInt();
                                if (!segmentsReceived.containsKey(seqNumPreview)) {
                                    System.out.println("!!! Pacote #" + seqNumPreview + " descartado para simulação de perda !!!");
                                    continue;
                                }
                            }


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
                                if (!segmentsReceived.containsKey(sequenceNumber)) {
                                    segmentsReceived.put(sequenceNumber, data);
                                    System.out.println("Pacote #" + sequenceNumber + " recebido e validado.");
                                }
                                if (flag == 1) {
                                    lastNumberByte = sequenceNumber;
                                }
                            } else {
                                System.out.println("Pacote #" + sequenceNumber + " corrompido e descartado!");
                            }
                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout! Verificando pacotes faltando...");
                            break;
                        }
                    }

                    if (erroServidor) break;

                    if (lastNumberByte == -1 && segmentsReceived.isEmpty()) {
                        tentativas++;
                        continue;
                    } else if (lastNumberByte == -1) {
                        tentativas++;
                        continue;
                    }

                    List<Integer> pacotesFaltantes = new ArrayList<>();
                    for (int i = 0; i <= lastNumberByte; i++) {
                        if (!segmentsReceived.containsKey(i)) {
                            pacotesFaltantes.add(i);
                        }
                    }
                    if (pacotesFaltantes.isEmpty()) {
                        transferenciaCompleta = true;
                    } else {
                        enviarNack(socket, pacotesFaltantes, inet, port);
                        tentativas++;
                    }
                }

                if (transferenciaCompleta) {
                    System.out.println("Montando o arquivo final...");
                    try (FileOutputStream fos = new FileOutputStream(archiveSaved)) {
                        for (byte[] segment : segmentsReceived.values()) {
                            fos.write(segment);
                        }
                        System.out.println("Arquivo '" + archiveSaved + "' salvo com sucesso!");
                    }
                } else if (!erroServidor) {
                    System.out.println("ERRO CRÍTICO: Transferência falhou após " + MAX_TENTATIVAS + " tentativas.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void enviarNack(DatagramSocket socket, List<Integer> pacotesFaltantes, InetAddress address, int port) throws IOException {
        String nackPayload = pacotesFaltantes.stream().map(String::valueOf).collect(Collectors.joining(","));
        String nackMessage = "NACK:" + nackPayload;
        byte[] nackBuffer = nackMessage.getBytes();
        DatagramPacket nackPacket = new DatagramPacket(nackBuffer, nackBuffer.length, address, port);
        socket.send(nackPacket);
        System.out.println("NACK enviado para os pacotes: " + nackPayload);
    }
}