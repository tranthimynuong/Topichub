# Stage 1: Build JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
# Tải trước dependencies để cache
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run Application with Lightweight JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Tạo thư mục uploads nếu cần lưu file
RUN mkdir -p /app/uploads
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Dserver.port=${PORT}", "-Dspring.thymeleaf.prefix=classpath:/templates/", "-Dspring.web.resources.static-locations=classpath:/static/", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
