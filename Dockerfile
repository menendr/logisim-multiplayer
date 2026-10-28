# SPDX-License-Identifier: GPL-2.0-or-later
FROM eclipse-temurin:17-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends python3 fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
WORKDIR /source
COPY client/ client/
COPY shared/ shared/
COPY server/ server/
COPY tests/ tests/
COPY lib/ lib/
COPY tools/ tools/
COPY THIRD_PARTY.md ./
COPY docs/dependency-sha256.json docs/dependency-sha256.json
COPY licenses/ licenses/
COPY third_party/ third_party/
RUN python3 tools/build_collaboration.py && python3 tools/test.py --headless

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core && rm -rf /var/lib/apt/lists/* && groupadd --gid 10001 logisim && useradd --uid 10001 --gid 10001 --no-create-home logisim
WORKDIR /app
COPY --from=build /source/dist/Logisim-Multiplayer-Server.jar /app/server.jar
RUN mkdir /app/data && chown logisim:logisim /app/data
USER 10001:10001
ENV PORT=8080 BIND_ADDRESS=0.0.0.0 DATA_DIR=/app/data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 CMD ["java", "-Djava.awt.headless=true", "-jar", "/app/server.jar", "--health"]
ENTRYPOINT ["java", "-Djava.util.prefs.userRoot=/tmp/logisim-prefs", "-Djava.awt.headless=true", "-Xms32m", "-Xmx256m", "-jar", "/app/server.jar"]
