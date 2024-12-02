# Use a imagem base do OpenJDK 17
FROM openjdk:23-jdk-slim

# Define o diretório de trabalho dentro do contêiner
WORKDIR /app

# Copia os arquivos do projeto para o contêiner
COPY . .

# Executa o comando de build do Gradle
RUN ./gradlew clean build

# Copia o arquivo JAR gerado pelo Gradle para o contêiner
COPY build/libs/*.jar app.jar

# Define o comando de inicialização do contêiner
ENTRYPOINT ["java", "-jar", "app.jar"]