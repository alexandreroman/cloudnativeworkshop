# Cloud Native Workshop: Stateless Apps

This lab explains why we need cloud native apps to be stateless,
and what solutions we can use to reach this goal.

This project shows how to create a simple microservice app using
Spring Boot and Spring Session together with Redis, in order to
replicate HTTP session attributes across all app instances.

This REST controller is not tied to Redis at all:
```java
@RestController
class IndexController {
    private static final String ATT_COUNTER = "counter";
    private String hostName;

    @PostConstruct
    private void init() throws UnknownHostException {
        // Get host name.
        hostName = InetAddress.getLocalHost().getCanonicalHostName();
    }

    @GetMapping("/")
    public String inc(HttpSession session) {
        // Use HttpSession to store a counter:
        // we do not store value in instance members since
        // this app is stateless.
        AtomicInteger counter = (AtomicInteger) session.getAttribute(ATT_COUNTER);
        if (counter == null) {
            // Initialize counter to 0.
            counter = new AtomicInteger();
        }

        // Increment counter.
        final int counterValue = counter.getAndIncrement();
        // Store counter in session:
        // this value is actually stored using a Redis datastore.
        // All app instances share the same datastore.
        session.setAttribute(ATT_COUNTER, counter);

        return "Counter value from " + hostName + ": " + counterValue;
    }
}
```

You only interact with Java EE HttpSession API: Spring Session will
automatically replicate session attributes under the hood.

This project is using Spring MVC to implement a REST controller.
Spring Session also works with legacy HttpServlet & REST controllers
you usually find in Java EE apps.
[Check out this project](https://github.com/alexandreroman/spring-session-demo)
to see Spring Session in action with a WAR file deployed to Tomcat.

## Compile this project

A JDK 8 is required to build this project:
```bash
$ ./mvnw clean package
```

## Run this app locally

Start a Redis server on your workstation.
You can use this Docker image:
```bash
$ docker run --rm --name redis -p "6379:6379/tcp" redis:5
```

Then invoke the main JAR file:
```bash
$ java -jar target/cn-stateless.jar
```

A web server is started on port `8080`, exposing a single REST
endpoint:
```bash
$ curl -b cookies.dat -c cookies.dat http://localhost:8080
Counter value from caprica: 0%
```

Set the environment variable `PORT` to change the web server port:
```bash
$ PORT=8090 java -jar target/cn-stateless.jar
```

If you invoke the other app instance now, you can see the value
has been replicated:
```bash
$ curl -b cookies.dat -c cookies.dat http://localhost:8090
Counter value from caprica: 1%
```

## Build a Docker image

In order to run this app in a cloud environment (such as Kubernetes),
you first need to build a Docker image:
```bash
$ docker build -t alexandreroman/cn-stateless .
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
ENTRYPOINT ["java", "-jar", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "./cn-stateless.jar"]
```

## Run this app as a Docker container

Run this app using `docker-compose`:
```bash
$ docker-compose -p cn-stateless up -d
Creating network "cn-stateless_default" with the default driver
Creating cn-stateless_redis_1         ... done
Creating cn-stateless_reverse_proxy_1 ... done
Creating cn-stateless_app_1           ... done
```

The `docker-compose` configuration file defines how to run
this app:
```yaml
version: "3.3"
services:
  app:
    build: .
    image: alexandreroman/cn-stateless
    environment:
      # Enable profile "cloud".
      - SPRING_PROFILES_ACTIVE=cloud
    links:
      - redis
    labels:
      - "traefik.enable=true"
      - "traefik.port=8080"
      - "traefik.frontend.rule=Host:app.stateless.cloudnative"
      - "traefik.backend.healthcheck.path=/actuator/health"

  # Store session attributes in a shared Redis datastore.
  redis:
    image: "redis:5"
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
 - a Redis datastore, using the official Docker image, with a single
   node configuration (no high availability, no persistence storage),
 - a reverse proxy using [Traefik](https://traefik.io/) to redirect
   traffic to all app instances.

Traefik is used here because `docker-compose` does not provides native
support for dynamically load balancing traffic across all app instances.
You could use a tool like `nginx` to load balance traffic, but then
you would need to update the configuration each time the number of
app instances is changed. Traefik can reconfigure itself by
discovering running app instances.

When `docker-compose` is started, the app is available
at http://localhost:8090 using the domain `app.stateless.cloudnative`:
```bash
$ curl -b cookies.dat -c cookies.dat -H "Host:app.stateless.cloudnative" http://localhost:8090
Counter value from 9222a2c13c22: 0%
```

Using `docker-compose`, you can easily scale out this app:
```bash
$ docker-compose -p cn-stateless up -d --scale app=3
cn-stateless_redis_1 is up-to-date
cn-stateless_reverse_proxy_1 is up-to-date
Starting cn-stateless_app_1 ... done
Creating cn-stateless_app_2 ... done
Creating cn-stateless_app_3 ... done
```

See changes as new app instances are started:
```bash
$ watch -n 1 curl -s -b cookies.dat -c cookies.dat -H "Host:app.stateless.cloudnative" http://localhost:8090
Counter value from 9222a2c13c22: 2%
Counter value from 5341e723ca09: 3%
Counter value from 5341e723ca09: 4%
```

Reduce the number app instances with the same command:
```bash
$ docker-compose -p cn-stateless up -d --scale app=1
cn-stateless_redis_1 is up-to-date
cn-stateless_reverse_proxy_1 is up-to-date
Stopping and removing cn-stateless_app_2 ... done
Stopping and removing cn-stateless_app_3 ... done
Starting cn-stateless_app_1              ... done
```

Kill all processes with this command:
```bash
$ docker-compose -p cn-stateless down
Stopping cn-stateless_app_1           ... done
Stopping cn-stateless_redis_1         ... done
Stopping cn-stateless_reverse_proxy_1 ... done
Removing cn-stateless_app_1           ... done
Removing cn-stateless_redis_1         ... done
Removing cn-stateless_reverse_proxy_1 ... done
Removing network cn-stateless_default
```

## Deploy this app to a Kubernetes cluster

Deploy this app to a Kubernetes cluster using the provided
deployment files:
```bash
$ kubectl apply -f k8s
namespace/cn-stateless created
deployment.apps/app created
deployment.apps/redis created
service/cn-stateless-lb created
service/redis created
```

This configuration defines 2 app instances with a shared Redis datastore:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
  namespace: cn-stateless
spec:
  # We can start many app instances, since each instance is stateless
  # and data is shared using the Redis datastore.
  replicas: 2
  selector:
    matchLabels:
      role: app
  template:
    metadata:
      labels:
        role: app
    spec:
      containers:
        - name: cn-stateless
          image: alexandreroman/cn-stateless
          env:
            # Enable profile "cloud".
            - name: SPRING_PROFILES_ACTIVE
              value: cloud
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
  name: redis
  namespace: cn-stateless
spec:
  replicas: 1
  selector:
    matchLabels:
      role: redis
  template:
    metadata:
      labels:
        role: redis
    spec:
      containers:
        - name: cn-stateless-redis
          image: redis:5
          ports:
            - containerPort: 6379
          livenessProbe:
            tcpSocket:
              port: 6379
          readinessProbe:
            tcpSocket:
              port: 6379
```

A load balancer is created to expose this app,
along with a cluster-wide Redis service:
```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: cn-stateless-lb
  labels:
    app: cn-stateless
  namespace: cn-stateless
spec:
  type: LoadBalancer
  ports:
    # We rely on the native load balancer support from Kubernetes
    # to redirect traffic across all app instances.
    - port: 80
      protocol: TCP
      targetPort: 8080
  selector:
    role: app
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: cn-stateless
spec:
  # We need to create a cluster-wide Redis datastore service,
  # since this instance is used by all app instances.
  ports:
    - name: redis
      port: 6379
      protocol: TCP
  selector:
    role: redis
```

Note we do not use Traefik when deploying to Kubernetes: we rely on
the native load balancer support in the cluster to redirect traffic
across all app instances. Pods are receiving traffic as long as
their healthcheck status is good.

Use this command to hit the microservice app:
```bash
$ curl -b cookies.dat -c cookies.dat http://localhost
Counter value from app-5c6759f54-ksnnn: 0%
```

Use this command to change the number of app instances:
```bash
$ kubectl -n cn-stateless scale --replicas=3 deployment/app
deployment.extensions/app scaled
```

Monitor app instances:
```bash
$ kubectl -n cn-stateless get pods
app-5c6759f54-gk7fc      1/1     Running   0          64s
app-5c6759f54-h7knq      1/1     Running   0          7m5s
app-5c6759f54-ksnnn      1/1     Running   0          7m5s
redis-65c47c7fb5-xqhln   1/1     Running   0          7m5s
```

Release resources using this command:
```bash
$ kubectl delete -f k8s
namespace "cn-stateless" deleted
deployment.apps "app" deleted
deployment.apps "redis" deleted
service "cn-stateless-lb" deleted
service "redis" deleted
```

## Deploy this app to Cloud Foundry

Prior to deploying this app to Cloud Foundry, you need to create
a Redis service instance. If you're using
[Pivotal Web Service](https://run.pivotal.io/), use this command:
```bash
$ cf create-service rediscloud 30mb redis
```

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
  - name: cn-stateless
    path: target/cn-stateless.jar
    random-route: true
    instances: 2
    services:
      - redis
```
