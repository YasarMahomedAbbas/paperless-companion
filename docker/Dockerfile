# Build environment: OpenJDK + Android SDK + Gradle, so nothing gets installed on the host.
FROM docker.io/library/eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS=16111833
ARG GRADLE_VERSION=9.8.2
ARG PLATFORM=android-37.0
# The build-tools version AGP 9.4 expects by default.
ARG BUILD_TOOLS=36.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$GRADLE_HOME/bin

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}" > /dev/null \
 && chmod -R a+rX $ANDROID_HOME

RUN curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv /opt/gradle-${GRADLE_VERSION} $GRADLE_HOME \
 && rm /tmp/gradle.zip

WORKDIR /src
