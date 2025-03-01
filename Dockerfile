FROM maven:3-eclipse-temurin-21 as build

RUN apt-get update && apt-get -y install git

RUN git clone https://github.com/imi-frankfurt/maven.git /maven
WORKDIR /maven
RUN git checkout 37dab8a52e2c38cb5d45a1c59cacaf4070c668ba && mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

RUN git clone https://github.com/imi-frankfurt/maven.spring.git /maven-spring
WORKDIR /maven-spring
RUN git checkout d716857d7e60d7e5a8fb213b93ea30c75958ad12 && mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

RUN git clone https://github.com/imi-frankfurt/dataelementhub.maven.git /dehub-parent
WORKDIR /dehub-parent
RUN git checkout 2bffcdf94cb2e210caa9ea67f84bf8b2e4e56612 && mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

RUN git clone https://github.com/imi-frankfurt/dataelementhub.maven.spring.git /dehub-maven-spring
WORKDIR /dehub-maven-spring
RUN git checkout 657516f56f73d7ab07872a3bf736bf258c7e018c && sed -i 's/<version>12.0.0<\/version>/<version>13.0.0<\/version>/' pom.xml
RUN mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

RUN git clone https://github.com/imi-frankfurt/dataelementhub.dal.git /dehub-dal
WORKDIR /dehub-dal
RUN git checkout 1f9cb07c4fed12dba3c09a846e89150c222efbf0 && mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

RUN git clone https://github.com/imi-frankfurt/dataelementhub.model.git /dehub-model
WORKDIR /dehub-model
RUN git checkout 0ae6275653b8b997253a1e7ad04549f95367d504 && sed -i 's/<version>11.3.0<\/version>/<version>13.0.0<\/version>/' pom.xml && sed -i 's/<version>1.18.24<\/version>/<version>1.18.30<\/version>/' pom.xml
RUN mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

COPY $PWD /mtbimporter
WORKDIR /mtbimporter

RUN mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true

FROM r-base:4.4.3

RUN apt-get update && apt-get install -y openjdk-21-jre docker-cli

COPY --from=build /mtbimporter/target/mtbimporter-*-jar-with-dependencies.jar /app/mtbimporter.jar
ENTRYPOINT ["java", "-jar", "/app/mtbimporter.jar"]