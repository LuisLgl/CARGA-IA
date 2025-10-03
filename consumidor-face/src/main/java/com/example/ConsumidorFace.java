package com.example;

import com.rabbitmq.client.*;
import smile.classification.KNN;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;


public class ConsumidorFace {
    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String ROUTING_KEY = "face.#";
    private static KNN<double[]> modelo;
    private static final String SAVE_DIR = "/app/imagens-recebidas-face";

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
        System.out.println(" [*] Consumidor de FACES (com IA Multi-Característica) aguardando imagens.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            byte[] imageBytes = delivery.getBody();
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));

                // Extrai o novo vetor de 6 características da imagem
                double[] features = extrairTodasFeatures(img);

                int pred = modelo.predict(features);
                String resultado = (pred == 1) ? "FELIZ" : "TRISTE";

                String fileName = String.format("%s_%d.jpg", resultado, System.currentTimeMillis());
                try (FileOutputStream fos = new FileOutputStream(SAVE_DIR + "/" + fileName)) {
                    fos.write(imageBytes);
                    System.out.println("     -> Imagem salva como: " + fileName);
                }

                System.out.println(" [x] Recebido '" + delivery.getEnvelope().getRoutingKey() + "'");
                TimeUnit.MILLISECONDS.sleep(800);
                System.out.println("     -> Resultado: " + resultado + (pred == 1 ? " 😀" : " 😢"));

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    /**
     * Treina o modelo usando o novo "super-vetor" de características.
     */
    private static void treinarModelo() {
        System.out.println("Treinando o modelo de IA do Smile com múltiplas características...");
        
        BufferedImage sampleHappy = criarImagemFaceDeExemplo(true);
        BufferedImage sampleSad = criarImagemFaceDeExemplo(false);

        double[] happyFeatures = extrairTodasFeatures(sampleHappy);
        double[] sadFeatures = extrairTodasFeatures(sampleSad);

        System.out.format("   - Features de 'Feliz': [R:%.0f, G:%.0f, B:%.0f, BocaSup:%.2f, BocaInf:%.2f, Diff:%.2f]\n", 
            happyFeatures[0], happyFeatures[1], happyFeatures[2], happyFeatures[3], happyFeatures[4], happyFeatures[5]);
        System.out.format("   - Features de 'Triste': [R:%.0f, G:%.0f, B:%.0f, BocaSup:%.2f, BocaInf:%.2f, Diff:%.2f]\n", 
            sadFeatures[0], sadFeatures[1], sadFeatures[2], sadFeatures[3], sadFeatures[4], sadFeatures[5]);

        double[][] features = { happyFeatures, happyFeatures, sadFeatures, sadFeatures };
        int[] labels = {1, 1, 0, 0};
        
        modelo = KNN.fit(features, labels, 3); 
        System.out.println("Modelo treinado!");
    }

    /**
     * Função principal que combina todas as características em um único vetor.
     */
    private static double[] extrairTodasFeatures(BufferedImage img) {
        double[] featuresBoca = extrairFeaturesBoca(img);
        double[] featuresCor = extrairFeaturesCorDeFundo(img);
        
        // Retorna um único array com 6 posições
        return new double[] {
            featuresCor[0],    // R médio do fundo
            featuresCor[1],    // G médio do fundo
            featuresCor[2],    // B médio do fundo
            featuresBoca[0],   // Proporção de preto na parte superior da boca
            featuresBoca[1],   // Proporção de preto na parte inferior da boca
            featuresBoca[2]    // Diferença entre as proporções
        };
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
        double topRatio = (topTotalPixels > 0) ? (double) topBlack / topTotalPixels : 0.0;
        double bottomRatio = (bottomTotalPixels > 0) ? (double) bottomBlack / bottomTotalPixels : 0.0;
        return new double[]{topRatio, bottomRatio, topRatio - bottomRatio};
    }
    
    /**
     * Nova função para extrair a cor média do fundo, amostrando os cantos.
     */
    private static double[] extrairFeaturesCorDeFundo(BufferedImage img) {
        if (img == null) return new double[]{0.0, 0.0, 0.0};
        
        // Pega a cor dos 4 cantos da imagem
        Color c1 = new Color(img.getRGB(1, 1));
        Color c2 = new Color(img.getRGB(img.getWidth() - 2, 1));
        Color c3 = new Color(img.getRGB(1, img.getHeight() - 2));
        Color c4 = new Color(img.getRGB(img.getWidth() - 2, img.getHeight() - 2));

        // Calcula a média dos componentes RGB
        double avgR = (c1.getRed() + c2.getRed() + c3.getRed() + c4.getRed()) / 4.0;
        double avgG = (c1.getGreen() + c2.getGreen() + c3.getGreen() + c4.getGreen()) / 4.0;
        double avgB = (c1.getBlue() + c2.getBlue() + c3.getBlue() + c4.getBlue()) / 4.0;
        
        return new double[]{avgR, avgG, avgB};
    }
    
    private static BufferedImage criarImagemFaceDeExemplo(boolean feliz) {
        // ... (código idêntico ao do Gerador para criar a imagem de exemplo)
        int width = 200, height = 200;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(feliz ? new Color(255, 250, 205) : new Color(220, 240, 255));
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(new Color(240, 200, 160));
        g2d.fillOval(30, 30, width - 60, height - 60);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(30, 30, width - 60, height - 60);
        g2d.fillOval(width / 3 - 5, height / 3, 10, 10);
        g2d.fillOval(2 * width / 3 - 5, height / 3, 10, 10);
        g2d.setStroke(new BasicStroke(3));
        if (feliz) {
            g2d.draw(new Arc2D.Double(width/2.0 - 30, 2*height/3.0 - 15, 60, 30, 180, 180, Arc2D.OPEN));
        } else {
            g2d.draw(new Arc2D.Double(width/2.0 - 30, 2*height/3.0, 60, 30, 0, 180, Arc2D.OPEN));
        }
        g2d.dispose();
        return img;
    }
}