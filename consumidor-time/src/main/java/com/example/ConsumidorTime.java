package com.example;

import com.rabbitmq.client.*;
import smile.classification.KNN;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConsumidorTime {
    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String ROUTING_KEY = "team.#";
    private static KNN<double[]> modelo;

    private static final String TRAIN_DIR = "/app/imagenstreino/image-times";
    private static final String SAVE_DIR = "/app/imagens-recebidas-time";

    public static void main(String[] args) throws IOException, TimeoutException {
        Files.createDirectories(Paths.get(TRAIN_DIR));
        Files.createDirectories(Paths.get(SAVE_DIR));

        treinarModelo();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setUsername("user");
        factory.setPassword("password");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        
        String queueName = "fila_times";
        boolean durable = true; 
        
        // <<< CORREÇÃO APLICADA AQUI >>>
        // Adicionada a linha que declara (cria) a fila antes de usá-la.
        channel.queueDeclare(queueName, durable, false, false, null);
        
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        System.out.println(" [*] Consumidor de TIMES aguardando imagens na fila '" + queueName + "'");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                byte[] imageBytes = delivery.getBody();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (img != null) {
                    double[] features = extrairFeatureDeCorDominante(img);
                    int predicao = modelo.predict(features);
                    
                    String time;
                    switch (predicao) {
                        case 0: time = "Flamengo"; break;
                        case 1: time = "Borussia Dortmund"; break;
                        case 2: time = "Gremio"; break;
                        case 3: time = "Fluminense"; break;
                        default: time = "Desconhecido"; break;
                    }

                    img = desenharTextoNaImagem(img, time);

                    String originalFileName = "desconhecido_" + System.currentTimeMillis() + ".png";
                    AMQP.BasicProperties props = delivery.getProperties();
                    Map<String, Object> headers = props.getHeaders();
                    if (headers != null && headers.containsKey("filename")) {
                        originalFileName = headers.get("filename").toString();
                    }

                    File outputFile = new File(SAVE_DIR + "/" + originalFileName);
                    ImageIO.write(img, "png", outputFile);

                    StringBuilder logMessage = new StringBuilder();
                    logMessage.append("[x] Recebido '").append(delivery.getEnvelope().getRoutingKey()).append("'\n");
                    logMessage.append("    -> Resultado da Inferência: ").append(time).append("\n");
                    logMessage.append("    -> Imagem salva como: ").append(originalFileName).append("\n");

                    System.out.println(logMessage.toString());
                    
                    TimeUnit.SECONDS.sleep(2);
                }
            } catch (InterruptedException e) {
                System.err.println("A thread foi interrompida durante a pausa.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        // Ótima adição! Garante que o consumidor só pegue uma mensagem por vez.
        channel.basicQos(1); 
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    private static BufferedImage desenharTextoNaImagem(BufferedImage img, String texto) {
        Graphics2D g = img.createGraphics();
        Font font = new Font("Arial", Font.BOLD, 36);
        g.setFont(font);
        g.setColor(Color.YELLOW);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawString(texto, 10, 40);
        g.dispose();
        return img;
    }

    private static void treinarModelo() {
        System.out.println("Iniciando treinamento do modelo de times com imagens de " + TRAIN_DIR);
        File diretorioDeTreino = new File(TRAIN_DIR);
        File[] arquivos = diretorioDeTreino.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg"));
        List<double[]> featuresList = new ArrayList<>();
        List<Integer> labelsList = new ArrayList<>();

        if (arquivos != null) {
            System.out.println("Encontrados " + arquivos.length + " arquivos de imagem para processar.");
            for (File arquivo : arquivos) {
                String nome = arquivo.getName().toLowerCase();
                try {
                    BufferedImage img = ImageIO.read(arquivo);
                    if (img == null) continue;

                    double[] features = extrairFeatureDeCorDominante(img);
                    Integer label = null;
                    
                    if (nome.startsWith("fluminense")) label = 3;
                    else if (nome.startsWith("flamengo")) label = 0;
                    else if (nome.startsWith("borussia-dortmund")) label = 1;
                    else if (nome.startsWith("gremio")) label = 2;

                    if (label != null) {
                        featuresList.add(features);
                        labelsList.add(label);
                    }
                } catch (IOException e) {
                    System.err.println("Erro de I/O ao ler imagem: " + arquivo.getName());
                }
            }
        }

        if (featuresList.size() > 1 && labelsList.stream().distinct().count() > 1) {
            double[][] x = featuresList.toArray(new double[0][]);
            int[] y = labelsList.stream().mapToInt(Integer::intValue).toArray();
            int k = 1;
            modelo = KNN.fit(x, y, k);
            System.out.println("✅ Modelo de IA para times treinado com " + x.length + " imagens!");
        } else {
            System.out.println("‼️ AVISO: Nenhuma imagem de treino válida foi encontrada. Usando modelo de fallback.");
            modelo = KNN.fit(new double[][]{{0,0,0,0}}, new int[]{99}, 1);
        }
    }

    private static double[] extrairFeatureDeCorDominante(BufferedImage img) {
        if (img == null) return new double[]{0,0,0,0};

        int redCount = 0, yellowCount = 0, blueCount = 0, greenCount = 0;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color pixel = new Color(img.getRGB(x, y), true);
                if (pixel.getAlpha() < 100) continue;

                int r = pixel.getRed();
                int g = pixel.getGreen();
                int b = pixel.getBlue();

                if ((r > 220 && g > 220 && b > 220) || (r < 40 && g < 40 && b < 40)) continue;

                if (r > g + 30 && r > b + 30) redCount++;
                else if (r > 180 && g > 180 && b < 100) yellowCount++;
                else if (g > r + 20 && g > b + 20) greenCount++;
                else if (b > r + 30 && b > g + 30) blueCount++;
            }
        }

        int maxCount = Math.max(Math.max(redCount, yellowCount), Math.max(blueCount, greenCount));

        if (maxCount == 0) return new double[]{0,0,0,0};
        
        if (maxCount == redCount) return new double[]{1, 0, 0, 0};
        if (maxCount == yellowCount) return new double[]{0, 1, 0, 0};
        if (maxCount == blueCount) return new double[]{0, 0, 1, 0};
        return new double[]{0, 0, 0, 1};
    }
}