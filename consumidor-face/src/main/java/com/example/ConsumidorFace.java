package com.example;

import com.rabbitmq.client.*;
import smile.classification.KNN;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConsumidorFace {
    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String ROUTING_KEY = "face.#";
    private static KNN<double[]> modelo;

    private static final String TRAIN_DIR = "/app/imagenstreino/image-faces";
    private static final String SAVE_DIR = "/app/imagens-recebidas-face";

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
        
        // <<< ALTERAÇÃO PARA NOMEAR A FILA >>>
        String queueName = "fila_faces"; // 1. Nome fixo para a fila
        boolean durable = true;          // 2. A fila sobreviverá a reinicializações
        channel.queueDeclare(queueName, durable, false, false, null); // 3. Declaração da fila
        
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        System.out.println(" [*] Consumidor de FACES aguardando imagens na fila '" + queueName + "'");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                byte[] imageBytes = delivery.getBody();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));

                if (img != null) {
                    double[] features = extrairTodasFeatures(img);
                    int predicao = modelo.predict(features);
                    String resultado = (predicao == 1) ? "FELIZ" : "TRISTE";

                    img = desenharTextoNaImagem(img, resultado);

                    String originalFileName = "desconhecido_" + System.currentTimeMillis() + ".jpg";

                    AMQP.BasicProperties props = delivery.getProperties();
                    Map<String, Object> headers = props.getHeaders();
                    if (headers != null && headers.containsKey("filename")) {
                        originalFileName = headers.get("filename").toString();
                    }

                    File outputFile = new File(SAVE_DIR + "/" + originalFileName);
                    ImageIO.write(img, "jpg", outputFile);

                    StringBuilder logMessage = new StringBuilder();
                    logMessage.append("[x] Recebido '").append(delivery.getEnvelope().getRoutingKey()).append("'\n");
                    logMessage.append("    -> Resultado da Inferência: ").append(resultado).append(predicao == 1 ? " 😀" : " 😢").append("\n");
                    logMessage.append("    -> Imagem salva como: ").append(originalFileName).append("\n");

                    System.out.println(logMessage.toString());
                    
                    TimeUnit.SECONDS.sleep(5);
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
        System.out.println("Iniciando treinamento do modelo de faces com imagens de " + TRAIN_DIR);
        File diretorioDeTreino = new File(TRAIN_DIR);
        File[] arquivos = diretorioDeTreino.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") ||
                name.toLowerCase().endsWith(".jpg") ||
                name.toLowerCase().endsWith(".jpeg")
        );
        List<double[]> featuresList = new ArrayList<>();
        List<Integer> labelsList = new ArrayList<>();
        if (arquivos != null) {
            System.out.println("Encontrados " + arquivos.length + " arquivos de imagem para processar.");
            for (File arquivo : arquivos) {
                String nome = arquivo.getName().toUpperCase();
                try {
                    BufferedImage img = ImageIO.read(arquivo);
                    if (img == null) {
                        System.err.println("AVISO: Falha ao ler o arquivo de imagem: " + arquivo.getName());
                        continue;
                    }
                    if (nome.startsWith("FELIZ")) {
                        featuresList.add(extrairTodasFeatures(img));
                        labelsList.add(1);
                    } else if (nome.startsWith("TRISTE")) {
                        featuresList.add(extrairTodasFeatures(img));
                        labelsList.add(0);
                    }
                } catch (IOException e) {
                    System.err.println("Erro de I/O ao ler imagem de treinamento: " + arquivo.getName());
                }
            }
        }
        if (featuresList.size() > 1 && labelsList.stream().distinct().count() > 1) {
            double[][] features = featuresList.toArray(new double[0][]);
            int[] labels = labelsList.stream().mapToInt(Integer::intValue).toArray();
            int k = Math.min(3, features.length - 1);
            if (k < 1) k = 1;
            modelo = KNN.fit(features, labels, k);
            System.out.println("✅ Modelo de IA para faces treinado com " + features.length + " imagens!");
        } else {
            System.out.println("‼️ AVISO: Não foram encontradas imagens suficientes. Usando modelo de fallback.");
            double[][] features = {{0, 0, 0, 0, 0, 0}, {1, 1, 1, 1, 1, 1}};
            int[] labels = {0, 1};
            modelo = KNN.fit(features, labels, 1);
        }
    }

    private static double[] extrairTodasFeatures(BufferedImage img) {
        double[] featuresBoca = extrairFeaturesBoca(img);
        double[] featuresCor = extrairFeaturesCorDeFundo(img);
        return new double[]{featuresCor[0], featuresCor[1], featuresCor[2], featuresBoca[0], featuresBoca[1], featuresBoca[2]};
    }

    private static double[] extrairFeaturesCorDeFundo(BufferedImage img) {
        if (img == null || img.getWidth() < 2 || img.getHeight() < 2) return new double[]{0.0, 0.0, 0.0};
        Color c1 = new Color(img.getRGB(1, 1));
        Color c2 = new Color(img.getRGB(img.getWidth() - 2, 1));
        Color c3 = new Color(img.getRGB(1, img.getHeight() - 2));
        Color c4 = new Color(img.getRGB(img.getWidth() - 2, img.getHeight() - 2));
        double avgR = (c1.getRed() + c2.getRed() + c3.getRed() + c4.getRed()) / 4.0;
        double avgG = (c1.getGreen() + c2.getGreen() + c3.getGreen() + c4.getGreen()) / 4.0;
        double avgB = (c1.getBlue() + c2.getBlue() + c3.getBlue() + c4.getBlue()) / 4.0;
        return new double[]{avgR, avgG, avgB};
    }

    private static double[] extrairFeaturesBoca(BufferedImage img) {
        if (img == null) return new double[]{0.0, 0.0, 0.0};
        int width = img.getWidth(), height = img.getHeight();
        int bocaTop = (int) (2 * height / 3.0) - 20, bocaBottom = bocaTop + 40;
        int bocaLeft = width / 2 - 40, bocaRight = width / 2 + 40;
        int midY = (bocaTop + bocaBottom) / 2;
        long topBlack = 0, bottomBlack = 0, topTotalPixels = 0, bottomTotalPixels = 0;
        for (int y = bocaTop; y < bocaBottom; y++) {
            for (int x = bocaLeft; x < bocaRight; x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    int rgb = img.getRGB(x, y);
                    boolean isBlack = ((rgb >> 16) & 0xFF) < 50 && ((rgb >> 8) & 0xFF) < 50 && (rgb & 0xFF) < 50;
                    if (y < midY) {
                        topTotalPixels++;
                        if (isBlack) topBlack++;
                    } else {
                        bottomTotalPixels++;
                        if (isBlack) bottomBlack++;
                    }
                }
            }
        }
        double topRatio = (topTotalPixels > 0) ? (double) topBlack / topTotalPixels : 0.0;
        double bottomRatio = (bottomTotalPixels > 0) ? (double) bottomBlack / bottomTotalPixels : 0.0;
        return new double[]{topRatio, bottomRatio, topRatio - bottomRatio};
    }
}