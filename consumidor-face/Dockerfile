# Estágio de Build
FROM maven:3.8.4-openjdk-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Estágio de Produção
FROM openjdk:11-jre-slim

#
# <<< ADICIONE AS LINHAS PARA AS BIBLIOTECAS AQUI >>>
#
# Instala as dependências de fontes no sistema operacional da imagem final
RUN apt-get update && apt-get install -y \
    libfreetype6 \
    fontconfig \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# ATENÇÃO: MUDE O NOME DO JAR PARA CADA SERVIÇO
COPY --from=build /app/target/consumidor-face-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
CMD ["java", "-jar", "app.jar"]