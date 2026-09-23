FROM vsl-backend-app:latest
WORKDIR /app

# Copy the pre-built Spring Boot jar
COPY target/stemotion-be-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
