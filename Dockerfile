# Build stages contain source only; local credentials never enter the context.
FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/angular.json frontend/tsconfig*.json ./
COPY frontend/src ./src
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/src/main ./src/main
COPY --from=frontend /build/frontend/dist/control-financiero-ui/browser/ ./src/main/resources/static/
RUN mvn -B -DskipTests package && cp target/*.jar /tmp/cofi.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=backend /tmp/cofi.jar ./cofi.jar
ENV SPRING_PROFILES_ACTIVE=railway
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=15 -XX:+UseSerialGC -Xss512k"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/cofi.jar"]
