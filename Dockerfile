FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/classes ./classes

EXPOSE 18388
CMD ["java", "-Dfile.encoding=UTF-8", "-cp", "/app/classes", "com.xiangqi.web.PublicWebMain"]
