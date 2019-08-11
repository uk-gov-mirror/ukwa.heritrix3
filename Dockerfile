FROM maven:3-jdk-8 AS build-env


# First copy the POMs...
COPY pom.xml /heritrix3/pom.xml
COPY commons/pom.xml /heritrix3/commons/pom.xml
COPY modules/pom.xml  /heritrix3/modules/pom.xml
COPY engine/pom.xml  /heritrix3/engine/pom.xml
COPY dist/pom.xml  /heritrix3/dist/pom.xml
COPY contrib/pom.xml  /heritrix3/contrib/pom.xml

# and download the dependencies:
WORKDIR /heritrix3
RUN mvn -B -f /heritrix3/pom.xml -s /usr/share/maven/ref/settings-docker.xml dependency:resolve-plugins dependency:go-offline

# Then copy the actual sources...
COPY commons /heritrix3/commons
COPY modules  /heritrix3/modules
COPY engine  /heritrix3/engine
COPY dist  /heritrix3/dist
COPY contrib  /heritrix3/contrib

# And build against cached dependencies:
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml -DskipTests install

# Now we can pop the binary into a separate container:
FROM java:8
COPY --from=build-env /heritrix3/dist/target/heritrix-*-dist.zip /heritrix-dist.zip
RUN unzip /heritrix-dist.zip && rm /heritrix-dist.zip && mv /heritrix-* /heritrix3

# Finish setup:
EXPOSE 8443

# Run in foreground, with a decent heap:
ENV FOREGROUND=true \
    JAVA_OPTS=-Xmx2g
    
# Set up default credentials:
ENV HERITRIX_USER=heritrix \
    HERITRIX_PASSWORD=heritrix

# Hook in H3 runner script:
CMD [ "/heritrix3/bin/heritrix", "-b", "0.0.0.0", "-a", "$HERITRIX_USER:$HERITRIX_PASSWORD" ]
