FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
COPY target/lib lib/
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "main.java.com.gameplatform.local.LocalServerApplication"]
