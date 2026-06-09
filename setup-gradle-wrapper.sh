#!/bin/bash
# Setup Gradle Wrapper JAR if missing
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    mkdir -p gradle/wrapper
    echo "Downloading gradle-wrapper.jar for Gradle 8.4..."
    curl -L -o gradle/wrapper/gradle-wrapper.jar https://repo.gradle.org/gradle/libs-releases-local/org/gradle/gradle-wrapper/8.4/gradle-wrapper-8.4.jar
    if [ $? -eq 0 ]; then
        echo "Successfully downloaded gradle-wrapper.jar"
    else
        echo "Failed to download gradle-wrapper.jar"
        exit 1
    fi
fi
echo "gradle-wrapper.jar is ready"
