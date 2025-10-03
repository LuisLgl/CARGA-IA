package com.example;

import com.rabbitmq.client.*;
import smile.classification.KNN;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream; // Import para salvar o arquivo
import java.io.IOException;
import java.nio.file.Files;     // Import para criar o diretório
import java.nio.file.Paths;     // Import para criar o diretório
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConsumidorTime {
    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String ROUTING_KEY = "team.#";
    private static KNN<double[]> modelo;
    private static final String SAVE_DIR = "/app/imagens-recebidas-time"; 

    public static void main(String[] args) throws IOException, TimeoutException {
        Files.createDirectories(Paths.get(SAVE_DIR));
        treinarModelo();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setUsername("user");
        factory.setPassword("password");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        System.out.println(" [*] Consumidor de TIMES (com IA aprimorada) aguardando imagens.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            byte[] imageBytes = delivery.getBody();
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                double[] features = extrairCorDoCanto(img);
                int pred = modelo.predict(features);
                
                String time;
                String timeParaArquivo; // Variável sem emojis para o nome do arquivo

                switch (pred) {
                    case 0:
                        time = "Flamengo ❤️🖤";
                        timeParaArquivo = "Flamengo";
                        break;
                    case 1:
                        time = "Palmeiras 💚🤍";
                        timeParaArquivo = "Palmeiras";
                        break;
                    case 2:
                        time = "Corinthians ⚪⚫";
                        timeParaArquivo = "Corinthians";
                        break;
                    case 4:
                        time = "Real Madrid ⚪👑";
                        timeParaArquivo = "Real_Madrid";
                        break;
                    case 5:
                        time = "Barcelona 🔵🔴";
                        timeParaArquivo = "Barcelona";
                        break;
                    default:
                        time = "Outro time (Genérico)";
                        timeParaArquivo = "Outro_Time";
                        break;
                }

                // --- LÓGICA DE SALVAMENTO REINTEGRADA ---
                String fileName = String.format("%s_%d.jpg", timeParaArquivo, System.currentTimeMillis());
                try (FileOutputStream fos = new FileOutputStream(SAVE_DIR + "/" + fileName)) {
                    fos.write(imageBytes);
                    System.out.println("     -> Imagem salva como: " + fileName);
                }
                // --- FIM DA LÓGICA DE SALVAMENTO ---

                System.out.println(" [x] Recebido '" + delivery.getEnvelope().getRoutingKey() + "'");
                System.out.println("     -> Resultado: " + time);
                TimeUnit.MILLISECONDS.sleep(800);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    private static void treinarModelo() {
        double[][] x = {
            {200.0, 0.0, 0.0},
            {0.0, 100.0, 0.0},
            {0.0, 0.0, 0.0},
            {255.0, 255.0, 255.0},
            {165.0, 0.0, 52.0}
        };
        int[] y = {0, 1, 2, 4, 5};

        modelo = KNN.fit(x, y, 1);
        System.out.println("Modelo de IA para times treinado com SMILE!");
    }

    private static double[] extrairCorDoCanto(BufferedImage img) {
        if (img == null) return new double[]{128.0, 128.0, 128.0};
        
        Color corDoPixel = new Color(img.getRGB(1, 1));
        
        return new double[]{
            corDoPixel.getRed(),
            corDoPixel.getGreen(),
            corDoPixel.getBlue()
        };
    }
}