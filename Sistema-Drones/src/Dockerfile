FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copia todos os arquivos .java da raiz para o container
COPY *.java ./

# Cria pasta bin e compila
RUN mkdir -p bin && javac -d bin *.java
