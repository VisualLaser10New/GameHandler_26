FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/*.jar app.jar
COPY target/lib lib/
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.gameplatform.central.CentralSystemApplication"]
