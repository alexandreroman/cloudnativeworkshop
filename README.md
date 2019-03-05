# Cloud Native Workshop

Welcome to the cloud native workshop, a guided tour for learning how to
create and run apps with modern technologies, such as
[Spring Boot](https://spring.io/projects/spring-boot),
[Kubernetes](https://kubernetes.io/)
and [Cloud Foundry](https://www.cloudfoundry.org/)!

[Get the slides](http://bit.ly/cloudnativeworkshop)
to better understand the purpose of this repository.

## Prerequisites

You need to set up your workstation with the following tools in order to
attend this workshop:
 - JDK 8
 - Docker ([Docker Desktop](https://www.docker.com/products/docker-desktop)
   is the preferred option)
 - Java IDE, such as [IntelliJ IDEA](https://www.jetbrains.com/idea/)
   (but Eclipse is fine!)

You may want to deploy the apps to your preferred cloud environment runtime:
 - Kubernetes: you need a 1.10+ K8s cluster
 - Cloud Foundry: use any provider, or easily
 [find one](http://trycloudfoundry.com)

## Here we go!

[Back to basics](01-helloworld): create a microservice using Spring Boot, package this app
using Docker, run the image using docker-compose and deploy the app to
Kubernetes or Cloud Foundry.

[Stateless apps](02-stateless): see the benefits of making your app
stateless, and solutions to help you achieving that goal.

[External configuration](03-config): externalize configuration with Spring Cloud,
so that you can work with the same application code in different environments.

## Contribute

Contributions are always welcome!

Feel free to open issues & send PR.

## License

Copyright &copy; 2019 [Pivotal Software, Inc](https://pivotal.io).

This project is licensed under the [Apache Software License version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
