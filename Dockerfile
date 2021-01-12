FROM hseeberger/scala-sbt:11.0.9.1_1.4.6_2.12.12

# these directories are meant to be mounted by the docker run command
RUN mkdir -p /opt/gyremock/wiremock
RUN mkdir -p /opt/gyremock/proto
RUN mkdir -p /opt/gyremock/lib

# this is to not error when no proto is supplied
RUN touch /opt/gyremock/proto/init.proto

# these will copy the source code over and get the image ready
COPY . /opt/gyremock/
WORKDIR /opt/gyremock
RUN sbt compile

EXPOSE 18080 50000
ENTRYPOINT ["sbt", "run"]
