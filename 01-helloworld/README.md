# Cloud Native Workshop: Hello world!

Welcome to the first lab of this cloud native workshop.

This project shows how to create a simple microservice app using
Spring Boot. A single REST controller is deployed, displaying the
famous greeting message: `Hello world!`.

This REST controller is implemented as follows:
```java
@RestController
class HelloController {
    @Value("${MESSAGE:Hello world!}")
    private String message;

    @GetMapping("/")
    public String hello() {
        return message;
    }
}
```

No XML / YAML configuration needed: just paste this source code
in your app, and you're done!

## Compile this project

A JDK 8 is required to build this project:
```bash
$ ./mvnw clean package
```

## Run this app locally

Just invoke the main JAR file:
```bash
$ java -jar target/cn-helloworld.jar
```

A web server is started on port `8080`, exposing a single REST
endpoint:
```bash
$ curl http://localhost:8080
Hello world!%
```

Set the environment variable `PORT` to change the web server port:
```bash
$ PORT=8090 java -jar target/cn-helloworld.jar
```

## Build a Docker image

In order to run this app in a cloud environment (such as Kubernetes),
you first need to build a Docker image:
```bash
$ docker build -t alexandreroman/cn-helloworld .
```

The Dockerfile is optimized for build time, by caching dependencies
in a dedicated layer, separated from the app source code.
The [builder pattern](https://docs.docker.com/develop/develop-images/multistage-build/)
is used here to build the app in a temporary container, before copying
the app artifacts to the final container:
```Dockerfile
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
ENTRYPOINT ["java", "-jar", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "./cn-helloworld.jar"]
```

## Run this app as a Docker container

You can easily start this app as a Docker container, but don't
forget to expose the port `8080` from outside the container:
```bash
$ docker run --rm -p 8090:8080 alexandreroman/cn-helloworld
```

An other option to run this app is to use `docker-compose`:
```bash
$ docker-compose -p cn-helloworld up
```

The `docker-compose` configuration file defines how to run
this app:
```yaml
version: "3.3"
services:
  app:
    build: .
    restart: always
    environment:
      # Override the greeting message using an environment variable.
      - MESSAGE="Bonjour le monde!"
    ports:
      - "8090:8080"
```

When the container is started, the app is available
at http://localhost:8090:
```bash
$ curl http://localhost:8090
Hello world!%
```

## Deploy this app to a Kubernetes cluster

Deploy this app to a Kubernetes cluster using the provided
deployment files:
```bash
$ kubectl apply -f k8s
namespace/cn-helloworld created
deployment.apps/cn-helloworld created
service/cn-helloworld-lb created
```

This app is running in a pod:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cn-helloworld
  namespace: cn-helloworld
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cn-helloworld
  template:
    metadata:
      labels:
        app: cn-helloworld
    spec:
      containers:
        # Deploy this app in a pod, using a Docker image.
        - name: cn-helloworld
          image: alexandreroman/cn-helloworld
          env:
            # Override the greeting message using an environment variable.
            - name: MESSAGE
              value: "Bonjour le monde!"
          ports:
            - containerPort: 8080
          # Monitor this app using a liveness probe:
          # the pod will be restarted if this endpoint returns an error.
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 90
          # The pod is only made public when the app is "ready",
          # probing this endpoint.
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 30
```

A load balancer is created to expose this app:
```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: cn-helloworld-lb
  labels:
    app: cn-helloworld
  namespace: cn-helloworld
spec:
  # Expose this app using a load balancer.
  type: LoadBalancer
  ports:
    - port: 80
      protocol: TCP
      targetPort: 8080
  selector:
    # Target this app running in a pod using a selector.
    app: cn-helloworld
```

## Deploy this app to Cloud Foundry

A single command is required to deploy this app to
Cloud Foundry:
```bash
$ cf push
```

You don't need to create a Docker image to run this app with
Cloud Foundry: the platform takes care of everything
(including creating a container, setting up healthchecks).
You only need to define this configuration file:
```yaml
---
applications:
  - name: cn-helloworld
    path: target/cn-helloworld.jar
    random-route: true
```
