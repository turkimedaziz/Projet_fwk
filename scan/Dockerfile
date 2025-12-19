FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests
ENTRYPOINT ["java", "-jar", "app/app-jar/target/scan-app-jar-0.0.1-SNAPSHOT.jar"]
