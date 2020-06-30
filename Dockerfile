FROM centos:7

RUN yum update -y
RUN yum install -y wget unzip
RUN yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel
RUN yum clean all

COPY . /usr/src/app

# Define working directory.
WORKDIR /usr/src/app
RUN wget http://www.gtlib.gatech.edu/pub/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.zip
RUN unzip apache-maven-3.5.0-bin.zip
ENV PATH="/usr/src/app/apache-maven-3.5.0/bin:${PATH}"
ENV JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk/"
EXPOSE 8080
RUN mvn install

ENTRYPOINT ["mvn", "jetty:run-exploded"]

