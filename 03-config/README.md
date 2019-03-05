# Cloud Native Workshop: External Configuration

This lab explains how you can externalize configuration, so that
you can share the same application code in different environments:
development, staging, production for instance.

This project leverages [Spring Boot](https://spring.io/projects/spring-boot)
and [Spring Cloud](https://spring.io/projects/spring-cloud) with a microservice
app, where configuration is loaded from a
[Git repository](https://github.com/alexandreroman/cloudnativeworkshop-config/tree/master/cn-config)
using a Spring Cloud Config Server instance.

The microservice is exposing a REST controller with a single configuration value:
```java
@RestController
@RefreshScope
class HelloController {
    @Value("${message:Hello default!}")
    private String message;

    @GetMapping("/")
    public String hello() {
        return message;
    }
}
```

Note how the configuration value is defined using the regular
`@Value` annotation. The value is set when the class instance is
created: your application code contains no reference to any configuration
sources. You could also define configuration values using a class
annotated with `@ConfigurationProperties`.

If you want to apply configuration updates to your beans
(while the application is running), you need to add the `@RefreshScope`
annotation to your class. This annotation is optional.

You need to define some properties in the configuration file
`bootstrap.yml` in order to set up Spring Cloud Config:
```yaml
spring:
  application:
    # Set application name in bootstrap configuration
    # in order to fetch application configuration before
    # Spring Boot actually starts.
    name: cn-config
  cloud:
    config:
      # Set URI to Spring Cloud Config Server.
      uri: http://localhost:8888
      fail-fast: true
```

The application name set by `spring.application.name` is
required, since this value is used to lookup the right
configuration from Spring Cloud Config Server.

You may want to set the property `spring.cloud.config.uri`
to define the address to your Spring Cloud Config Server
(default value is http://localhost:8888). As you'll see
in this lab, we'll use different values depending on where
the app is running (local, Docker, Kubernetes).

Keep in mind you can override any Spring Boot configuration
properties using environment variables. For example:
set the environment variable `SPRING_CLOUD_CONFIG_URI` to
set the value for `spring.cloud.config.uri`.

The Spring Cloud Config Server configuration is defined as
below:
```yaml
spring:
  cloud:
    config:
      server:
        git:
          # Set Git repository location where configuration is stored.
          # The repository follows this layout:
          #   o- ${spring.application.name}
          #   |--- application.properties
          #   |--- application-${profile}.properties
          #   o- ...
          #   |--- application.properties
          uri: https://github.com/alexandreroman/cloudnativeworkshop-config.git
          search-paths: "{application}"
          clone-on-start: true

server:
  port: 8888
```

Here we load configuration values from a Git repository.
Note this Git repository follows a simple layout:
each directory maps to a Spring Boot application name, and
contains configuration properties for each profile.

## Compile this project

A JDK 8 is required to build this project:
```bash
$ ./mvnw clean package
```

## Run this app locally

Start the Spring Cloud Config Server instance:
```bash
$ java -jar config-server/target/cn-config-server.jar
```

Start the microservice app:
```bash
$ java -jar app/target/cn-config.jar
```

A web server is started on port `8080`, exposing a single REST
endpoint:
```bash
$ curl http://localhost:8080
Hello from Git repo!%
```

The configuration value has been loaded from the Git repository.
Now let's restart this app with a different profile:
```bash
$ SPRING_PROFILES_ACTIVE=dev java -jar app/target/cn-config.jar
```

A new value is loaded because we used profile `dev`:
```bash
$ curl http://localhost:8080
Hello developers!%
```

If a change is made to configuration files, you still need to
trigger a configuration refresh for your apps. In order to apply
the new configuration, you need to hit the endpoint `/actuator/refresh`:
```bash
$ curl -X POST http://localhost:8080/actuator/refresh
```

## Build Docker images

In order to run this app in a cloud environment (such as Kubernetes),
you first need to build a Docker image:
```bash
$ docker build -t alexandreroman/cn-config .
```

You also need to build a Docker image for Spring Cloud Config Server:
```bash
$ docker build -t alexandreroman/cn-config-server -f Dockerfile-config-server .
```

## Run this app as a Docker container

Run this app using `docker-compose`:
```bash
$ docker-compose -p cn-config up -d
Creating network "cn-config_default" with the default driver
Creating cn-config_sccs_1          ... done
Creating cn-config_reverse_proxy_1 ... done
Creating cn-config_app_1           ... done
```

The `docker-compose` configuration file defines how to run
this app:
```yaml
version: "3.3"
services:
  app:
    build: .
    image: alexandreroman/cn-config
    restart: always
    environment:
      # Set Spring Cloud Server URL.
      - SPRING_CLOUD_CONFIG_URI=http://sccs:8888
      # Enable profile "prod".
      - SPRING_PROFILES_ACTIVE=prod
    links:
      - sccs
    depends_on:
      - sccs
    labels:
      - "traefik.enable=true"
      - "traefik.port=8080"
      - "traefik.frontend.rule=Host:app.config.cloudnative"
      - "traefik.backend.healthcheck.path=/actuator/health"

  # Access to external configuration using a Spring Cloud Config Server instance.
  sccs:
    build: Dockerfile-config-server
    image: alexandreroman/cn-config-server
    expose:
      - "8888"
    labels:
      - "traefik.enable=false"

  # Use Traefik as a reverse proxy for all app instances.
  reverse_proxy:
    image: "traefik:1.7-alpine"
    # Set Traefik options:
    #  - web:    enable Traefik console
    #  - docker: listen to Docker events
    command: --web --docker
    labels:
      - "traefik.enable=false"
    ports:
      # Public port.
      - "8090:80"
      # Console port.
      - "9000:8080"
    volumes:
      # Give access to Docker events from Traefik.
      - /var/run/docker.sock:/var/run/docker.sock
```

Three components are defined in this deployment:
 - the microservice app, using the Docker image built from this project,
 - a Spring Cloud Config Server, reading configuration files from a Git
   repository,
 - a reverse proxy using [Traefik](https://traefik.io/) to redirect
   traffic to all app instances.

When `docker-compose` is started, the app is available
at http://localhost:8090 using the domain `app.config.cloudnative`:
```bash
$ curl -H "Host:app.config.cloudnative" http://localhost:8090
Hello production!%
```

Kill all processes with this command:
```bash
$ docker-compose -p cn-config down
Stopping cn-config_app_1           ... done
Stopping cn-config_reverse_proxy_1 ... done
Stopping cn-config_sccs_1          ... done
Removing cn-config_app_1           ... done
Removing cn-config_reverse_proxy_1 ... done
Removing cn-config_sccs_1          ... done
Removing network cn-config_default
```

## Deploy this app to a Kubernetes cluster

Deploy this app to a Kubernetes cluster using the provided
deployment files:
```bash
$ kubectl apply -f k8s
namespace/cn-config created
deployment.apps/app created
deployment.apps/sccs created
service/cn-config-lb created
service/sccs created
```

This configuration defines 2 pods: a microservice app and its
Spring Cloud Config Server instance:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
  namespace: cn-config
spec:
  replicas: 1
  selector:
    matchLabels:
      role: app
  template:
    metadata:
      labels:
        role: app
    spec:
      containers:
        - name: cn-config
          image: alexandreroman/cn-config
          env:
            # Set Spring Cloud Server URL.
            - name: SPRING_CLOUD_CONFIG_URI
              value: http://sccs:8888
            # Enable profile "prod".
            - name: SPRING_PROFILES_ACTIVE
              value: prod
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 90
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 30
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sccs
  namespace: cn-config
spec:
  replicas: 1
  selector:
    matchLabels:
      role: sccs
  template:
    metadata:
      labels:
        role: sccs
    spec:
      containers:
        - name: cn-config-server
          image: alexandreroman/cn-config-server
          ports:
            - containerPort: 8888
          livenessProbe:
            httpGet:
              port: 8888
              path: /actuator/health
            initialDelaySeconds: 90
          readinessProbe:
            httpGet:
              port: 8888
              path: /actuator/health
            initialDelaySeconds: 30
```

A load balancer is created to expose this app,
along with a cluster-wide Spring Cloud Config Server service:
```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: cn-config-lb
  labels:
    app: cn-config
  namespace: cn-config
spec:
  type: LoadBalancer
  ports:
    - port: 80
      protocol: TCP
      targetPort: 8080
  selector:
    role: app
---
apiVersion: v1
kind: Service
metadata:
  name: sccs
  namespace: cn-config
spec:
  # We need to create a cluster-wide Config Server service,
  # since this instance is used by all app instances.
  ports:
    - name: sccs
      port: 8888
      protocol: TCP
  selector:
    role: sccs
```

Note we do not use Traefik when deploying to Kubernetes: we rely on
the native load balancer support in the cluster to redirect traffic
across all app instances. Pods are receiving traffic as long as
their healthcheck status is good.

Use this command to hit the microservice app:
```bash
$ curl http://localhost
Hello production!%
```

Release resources using this command:
```bash
$ kubectl delete -f k8s
namespace "cn-config" deleted
deployment.apps "app" deleted
deployment.apps "sccs" deleted
service "cn-config-lb" deleted
service "sccs" deleted
```

## Deploy this app to Cloud Foundry

Prior to deploying this app to Cloud Foundry, you need to create
a Spring Cloud Config Server instance. If you're using
[Pivotal Web Service](https://run.pivotal.io/), use this command:
```bash
$ cf create-service -c '{"git": { "uri": "https://github.com/alexandreroman/cloudnativeworkshop-config", "cloneOnStart": "true", "searchPaths": "cn-config" }}' p-config-server trial config-server
```

Service creation can take up to 3 minutes. Monitor service creation
using this command:
```bash
$ cf service config-server
```

When you're reeady, use this single command to deploy the app
to Cloud Foundry:
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
  - name: cn-config
    path: app/target/cn-config.jar
    random-route: true
    env:
      SPRING_PROFILES_ACTIVE: prod
    services:
      - config-server
```
