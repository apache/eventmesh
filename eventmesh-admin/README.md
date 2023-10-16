# Getting Started

## Build on source code

```
cd eventmesh-console
./gradlew clean bootJar
```

```
java -jar build/libs/eventmesh-console-0.0.1-SNAPSHOT.jar
```

## Build and Run with Docker

```
cd eventmesh-console
./gradlew clean bootJar
docker build -t yourname/eventmesh-console -f docker/Dockerfile .
```

```
docker run -d --name eventmesh-console -p 8080:8080 yourname/eventmesh-console
```