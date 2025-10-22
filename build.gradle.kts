plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.misz.kon"
version = "1.0"
description = "Plugin do przyzywania koni inspirowany grą Wiedźmin 3"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    shadowJar {
        archiveBaseName.set("Kon")
        archiveClassifier.set("")
        archiveVersion.set("${project.version}")
    }

    build {
        dependsOn(shadowJar)
    }
}