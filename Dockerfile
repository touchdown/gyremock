FROM hseeberger/scala-sbt:11.0.2-oraclelinux7_1.4.6_2.12.12
RUN mkdir -p /opt/app/wiremock
RUN mkdir -p /opt/app/proto
RUN mkdir -p /opt/app/lib
RUN touch /opt/app/proto/init.proto

COPY . /opt/app/
WORKDIR /opt/app
RUN sbt compile || return 0

EXPOSE 8888 50000
ENTRYPOINT ["sbt", "run"]
