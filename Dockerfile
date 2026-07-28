FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -DskipTests clean package

FROM debian:bookworm-slim AS engines

ARG PIKAFISH_URL=https://github.com/official-pikafish/Pikafish/releases/download/Pikafish-2026-01-02/Pikafish.2026-01-02.7z
ARG PIKAFISH_SHA256=84257063905615919fb4ee6a70273a94843bb6ec04c45e3ac706098838bc1a49
ARG RAPFI_URL=https://github.com/dhbloo/rapfi/releases/download/250615/Rapfi-engine.7z
ARG RAPFI_SHA256=1a3e24024062a153ac079060ee9589a37c6bdd1ecc54fed3908793c519594e05
ARG RAPFI_LICENSE_URL=https://raw.githubusercontent.com/dhbloo/rapfi/250615/Copying.txt
ARG RAPFI_LICENSE_SHA256=6bab265559d5ebe9f259f2f54e9640bb4c8c8535bacd37c18125231f6fa9acf7

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl p7zip-full \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/engines
RUN set -eux; \
    curl -fL --retry 3 -o pikafish.7z "$PIKAFISH_URL"; \
    echo "$PIKAFISH_SHA256  pikafish.7z" | sha256sum -c -; \
    curl -fL --retry 3 -o rapfi.7z "$RAPFI_URL"; \
    echo "$RAPFI_SHA256  rapfi.7z" | sha256sum -c -; \
    curl -fL --retry 3 -o rapfi-copying.txt "$RAPFI_LICENSE_URL"; \
    echo "$RAPFI_LICENSE_SHA256  rapfi-copying.txt" | sha256sum -c -; \
    7z x -y -opikafish pikafish.7z >/dev/null; \
    7z x -y -orapfi rapfi.7z >/dev/null; \
    install -d /opt/engines/pikafish /opt/engines/rapfi; \
    install -m 0755 pikafish/Linux/pikafish-avx2 /opt/engines/pikafish/pikafish; \
    install -m 0644 pikafish/pikafish.nnue pikafish/Copying.txt pikafish/NNUE-License.md \
      pikafish/AUTHORS /opt/engines/pikafish/; \
    install -m 0755 rapfi/pbrain-rapfi-linux-clang-avx2 /opt/engines/rapfi/rapfi; \
    install -m 0644 rapfi/config.toml rapfi/model210901.bin rapfi/*.bin.lz4 rapfi/AUTHORS \
      /opt/engines/rapfi/; \
    install -m 0644 rapfi-copying.txt /opt/engines/rapfi/Copying.txt

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency
COPY --from=engines /opt/engines /opt/engines
COPY deploy/engines/pikafish-engine /usr/local/bin/pikafish-engine
COPY deploy/engines/rapfi-engine /usr/local/bin/rapfi-engine
RUN chmod 0755 /usr/local/bin/pikafish-engine /usr/local/bin/rapfi-engine

EXPOSE 18388
CMD ["java", "-Dfile.encoding=UTF-8", "-cp", "/app/classes:/app/dependency/*", "com.xiangqi.web.PublicWebMain"]
