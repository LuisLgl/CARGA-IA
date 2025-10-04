package com.example;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class GeradorMensagens {

    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String BASE_IMAGE_PATH = "/app/imagens";
    private static final Random random = new Random();

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setUsername("user");
        factory.setPassword("password");

        // <<< ALTERAÇÃO 1: Listas separadas para cada tipo de imagem >>>
        List<File> faceFiles = new ArrayList<>();
        List<File> teamFiles = new ArrayList<>();
        
        // Popula a lista de faces
        addFilesFromDir(new File(BASE_IMAGE_PATH, "image-faces"), faceFiles);
        // Popula a lista de times
        addFilesFromDir(new File(BASE_IMAGE_PATH, "image-times"), teamFiles);

        if (faceFiles.isEmpty() || teamFiles.isEmpty()) {
            System.err.println("Erro: Verifique se as pastas 'image-faces' e 'image-times' existem e contêm imagens.");
            return;
        }

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            System.out.println("Gerador pronto para enviar lotes equilibrados de imagens.");

            while (true) {
                System.out.println("\n--- Enviando lote equilibrado (3 faces, 2 times) ---");

                // <<< ALTERAÇÃO 2: Laços de envio separados >>>
                
                // Envia 3 imagens de faces
                for (int i = 0; i < 3; i++) {
                    File imageFile = faceFiles.get(random.nextInt(faceFiles.size()));
                    publishImage(channel, "face.image", imageFile);
                }

                // Envia 2 imagens de times
                for (int i = 0; i < 2; i++) {
                    File imageFile = teamFiles.get(random.nextInt(teamFiles.size()));
                    publishImage(channel, "team.logo", imageFile);
                }

                System.out.println("--- Lote enviado. Aguardando 2 segundos... ---");
                TimeUnit.SECONDS.sleep(2);
            }
        }
    }

    /**
     * <<< NOVO MÉTODO AUXILIAR >>>
     * Encapsula a lógica de publicação de uma imagem para evitar repetição de código.
     */
    private static void publishImage(Channel channel, String routingKey, File imageFile) throws IOException {
        byte[] messageBody = Files.readAllBytes(imageFile.toPath());

        Map<String, Object> headers = new HashMap<>();
        headers.put("filename", imageFile.getName());

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .build();

        channel.basicPublish(EXCHANGE_NAME, routingKey, props, messageBody);
        System.out.println(" [x] Enviado '" + routingKey + "':'" + imageFile.getName() + "'");
    }

    /**
     * Adiciona recursivamente todos os arquivos de um diretório a uma lista.
     */
    private static void addFilesFromDir(File directory, List<File> list) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        addFilesFromDir(file, list);
                    } else if (file.isFile() && (file.getName().endsWith(".png") || file.getName().endsWith(".jpg"))) {
                        list.add(file);
                    }
                }
            }
        }
    }
}