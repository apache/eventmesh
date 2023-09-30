plugins {
    java
    idea
    id("org.springframework.boot") version "2.7.15"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
}

group = "com.apache.eventmesh"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    mavenCentral()
}

// properties defined here can be referenced in subprojects like this: rootProject.extra["propName"]
// or： val propName: String by rootProject.extra

// utility
val commonsLang3Version by extra("3.13.0")
val guavaVersion by extra("32.1.2-jre") // not used for now
val fastjsonVersion by extra("2.0.40")
// swagger
val springdocVersion by extra("1.7.0")
// unit test
val mockitoVersion by extra("5.5.0")
// meta
val nacosVersion by extra("2.2.4")

dependencies {
    // versions managed by spring.dependency-management
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // utility
    implementation("org.apache.commons:commons-lang3:${commonsLang3Version}")
    implementation("com.alibaba.fastjson2:fastjson2:${fastjsonVersion}")
    // swagger
    implementation("org.springdoc:springdoc-openapi-ui:${springdocVersion}")
    implementation("org.springdoc:springdoc-openapi-javadoc:${springdocVersion}")
    annotationProcessor("com.github.therapi:therapi-runtime-javadoc-scribe:0.15.0")
    // unit test
    testImplementation("org.mockito:mockito-core:${mockitoVersion}")
    // meta
    implementation("com.alibaba.nacos:nacos-client:${nacosVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
