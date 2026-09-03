FROM eclipse-temurin:21-jdk

RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-fra poppler-utils \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/*-exec.jar app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
