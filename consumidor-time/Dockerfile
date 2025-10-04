# Estágio de Build (continua igual)
FROM maven:3.8.4-openjdk-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Estágio de Produção
FROM openjdk:11-jre-slim
WORKDIR /app

# <<< LINHA CORRIGIDA E MELHORADA >>>
# Instala as configurações de fontes (fontconfig) e um pacote de fontes padrão (fonts-dejavu).
# Isso resolve o NullPointerException no FontConfiguration do Java.
RUN apt-get update && apt-get install -y fontconfig fonts-dejavu

# O restante do Dockerfile continua igual
COPY --from=build /app/target/consumidor-time-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
CMD ["java", "-jar", "app.jar"]