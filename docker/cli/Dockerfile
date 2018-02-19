FROM ubuntu:xenial

ENV REQUIRED_PACKAGES openjdk-8-jre-headless libpfm4 libbluetooth3 libbluetooth-dev

RUN apt-get update && \
    apt-get install -y ${REQUIRED_PACKAGES} && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY powerapi-cli/target/universal/stage /powerapi-cli

WORKDIR /powerapi-cli

ENTRYPOINT ["/powerapi-cli/bin/powerapi"]