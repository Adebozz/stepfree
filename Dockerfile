# One image: the React build is served by Spring Boot, so the SPA and API share an origin and the
# session cookie stays first-party (risk R2). Tests run in CI, not here.

# --- 1. Frontend build ---
FROM node:25-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- 2. Backend build, with the SPA as static resources ---
FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /app/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /app/frontend/dist/ src/main/resources/static/
RUN ./mvnw -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# --- 3. Runtime: JRE only, non-root ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S stepfree && adduser -S stepfree -G stepfree
WORKDIR /app
# Layered copy: dependencies change rarely, so rebuilds reuse those layers.
COPY --from=backend /app/backend/target/extracted/dependencies/ ./
COPY --from=backend /app/backend/target/extracted/spring-boot-loader/ ./
COPY --from=backend /app/backend/target/extracted/snapshot-dependencies/ ./
COPY --from=backend /app/backend/target/extracted/application/ ./
USER stepfree
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
