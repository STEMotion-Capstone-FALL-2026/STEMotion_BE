FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 stemotion && useradd --uid 10001 --gid stemotion --no-create-home stemotion
COPY --from=build --chown=stemotion:stemotion /build/target/stemotion-be-*.jar /app/app.jar
USER stemotion
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
