package com.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class GeradorMensagens {

    private static final String EXCHANGE_NAME = "imagens_exchange";
    private static final String BASE_IMAGE_PATH = "/app/imagens-geradas";
    private static final Random random = new Random(); // Movido para ser acessível por todos os métodos

    public static void main(String[] args) throws Exception {
        System.out.println("Gerando imagens sintéticas de teste...");
        gerarImagensDeTeste();
        System.out.println("Imagens geradas com sucesso!");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setUsername("user");
        factory.setPassword("password");

        List<File> imageFiles = new ArrayList<>();
        addFilesFromDir(new File(BASE_IMAGE_PATH), imageFiles);

        if (imageFiles.isEmpty()) {
            System.err.println("Erro: Nenhuma imagem encontrada na pasta interna.");
            return;
        }
        
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            System.out.println("Gerador pronto para enviar imagens.");

            while (true) {
                File imageFile = imageFiles.get(random.nextInt(imageFiles.size()));
                String parentDir = imageFile.getParentFile().getName();
                String routingKey = parentDir.equals("faces") ? "face.image" : "team.logo";
                
                byte[] messageBody = Files.readAllBytes(imageFile.toPath());
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, messageBody);
                System.out.println(" [x] Enviado '" + routingKey + "':'" + imageFile.getName() + "'");

                TimeUnit.MILLISECONDS.sleep(200);
            }
        }
    }
    
    /**
     * Gera os lotes de imagens para faces e times.
     */
    private static void gerarImagensDeTeste() throws IOException {
        String facesDir = BASE_IMAGE_PATH + "/faces";
        String timesDir = BASE_IMAGE_PATH + "/times";
        new File(facesDir).mkdirs();
        new File(timesDir).mkdirs();

        // Gerar 10 faces de cada sentimento com cores variadas
        for (int i = 1; i <= 10; i++) {
            criarImagemFace(String.format("%s/face_feliz_%02d.jpg", facesDir, i), true);
            criarImagemFace(String.format("%s/face_triste_%02d.jpg", facesDir, i), false);
        }

        // Gerar imagens para os times especificados
        String[] times = {"flamengo", "palmeiras", "corinthians", "real_madrid", "barcelona"};
        for (String time : times) {
            for (int i = 1; i <= 2; i++) {
                criarImagemTime(String.format("%s/logo_%s_%d.jpg", timesDir, time, i), time);
            }
        }
    }

    /**
     * Gera uma imagem de rosto com cor de fundo aleatória baseada no sentimento.
     */
    private static void criarImagemFace(String filePath, boolean feliz) throws IOException {
        int width = 200, height = 200;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // --- LÓGICA DE COR APRIMORADA ---
        Color backgroundColor;
        if (feliz) {
            // Gera tons quentes aleatórios (amarelos, laranjas, rosas claros)
            int r = 255;
            int g = random.nextInt(106) + 150; // de 150 a 255
            int b = random.nextInt(151) + 50;  // de 50 a 200
            backgroundColor = new Color(r, g, b);
        } else {
            // Gera tons frios aleatórios (azuis, roxos, cianos claros)
            int r = random.nextInt(101) + 100; // de 100 a 200
            int g = random.nextInt(101) + 150; // de 150 a 250
            int b = 255;
            backgroundColor = new Color(r, g, b);
        }
        g2d.setColor(backgroundColor);
        g2d.fillRect(0, 0, width, height);

        // O resto do desenho continua o mesmo
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
            g2d.draw(new Arc2D.Double(width/2.0 - 30, 2*height/3.0 + 15, 60, 30, 0, 180, Arc2D.OPEN));
        }
        
        g2d.dispose();
        ImageIO.write(img, "jpg", new File(filePath));
    }

    /**
     * Gera uma imagem de time com base em um mapa de cores, agora incluindo Real Madrid e Barcelona.
     */
    private static void criarImagemTime(String filePath, String time) throws IOException {
        // --- MAPA DE CORES ATUALIZADO ---
        Map<String, Color[]> coresTimes = Map.of(
            "flamengo",    new Color[]{new Color(200, 0, 0), Color.BLACK},
            "palmeiras",   new Color[]{new Color(0, 100, 0), Color.WHITE},
            "corinthians", new Color[]{Color.BLACK, Color.WHITE},
            "real_madrid", new Color[]{Color.WHITE, new Color(254, 205, 5)}, // Branco e Dourado
            "barcelona",   new Color[]{new Color(165, 0, 52), new Color(0, 76, 151)}  // Grená e Azul
        );
        Color[] cores = coresTimes.getOrDefault(time, new Color[]{Color.GRAY, Color.WHITE});

        int width = 200, height = 200;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();

        // Usa as cores para criar um padrão simples
        g2d.setColor(cores[0]);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(cores[1]);
        g2d.setStroke(new BasicStroke(8));
        g2d.drawRect(20, 20, width - 40, height - 40);
        
        g2d.dispose();
        ImageIO.write(img, "jpg", new File(filePath));
    }

    private static void addFilesFromDir(File directory, List<File> list) {
        if (directory.exists() && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    addFilesFromDir(file, list);
                } else if (file.isFile()) {
                    list.add(file);
                }
            }
        }
    }
}