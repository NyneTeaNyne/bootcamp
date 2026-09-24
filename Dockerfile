# 1. Stage de build avec Java 25
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

# Copier l'ensemble du projet multi-modules
COPY . .

# Compiler et empaqueter tous les modules
RUN mvn clean package -DskipTests

# 2. Stage d'exécution Java 25
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copier le fichier JAR exécutable généré dans le module API
COPY --from=builder /app/apis/order-manager-api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]