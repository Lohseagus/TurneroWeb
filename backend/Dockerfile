# Imagen de producción del backend.
# Los tests corren en GitHub Actions (mvn test). Acá solo se empaqueta el JAR.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/backend-*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# PORT lo define docker-compose (8080 en el servidor). JAVA_TOOL_OPTIONS también.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
