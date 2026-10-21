FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
EXPOSE 8080
ADD ./target/skillb-ia-0.0.1-SNAPSHOT.war app.war
ENTRYPOINT ["java", "-jar", "app.war"]
