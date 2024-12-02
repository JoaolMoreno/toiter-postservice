# Limpa e constrói o projeto usando Gradle
./gradlew clean build

# Cria a imagem Docker
docker build -t lopesz3r4/toiter-post-service:latest .

# Faz o push da imagem para o Docker Hub
docker push lopesz3r4/toiter-post-service:latest