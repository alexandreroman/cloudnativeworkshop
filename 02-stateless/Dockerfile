# We use pattern builder to compile & package our application
FROM maven:3.6-jdk-8-alpine as maven
WORKDIR /home

# First we want to download project dependencies to optimize build time:
# dependencies will be cached for later use
COPY pom.xml pom.xml
RUN mvn dependency:go-offline -B

# Then we can copy project source files
COPY src src

# Finally project compilation can start
RUN mvn package

# The final Docker image starts here
FROM openjdk:8-jre-alpine
WORKDIR /app

# Copy application artifacts to this image
COPY --from=maven /home/target/*.jar ./

# Make sure we do not run this application as root
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -G appuser -S appuser appuser
USER appuser

# Now it's time to start the application:
# note the additional JVM options required to properly run inside a Docker container
ENTRYPOINT ["java", "-jar", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "./cn-stateless.jar"]
