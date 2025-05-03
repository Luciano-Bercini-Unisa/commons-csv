FROM openjdk:8-jre-alpine
COPY target/commons-csv-1.13.0-SNAPSHOT.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
