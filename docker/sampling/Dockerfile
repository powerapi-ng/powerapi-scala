FROM ubuntu:xenial

ENV REQUIRED_PACKAGES openjdk-8-jre-headless libpfm4 libbluetooth3 libbluetooth-dev psmisc cpulimit stress

RUN apt-get update && \
    apt-get install -y ${REQUIRED_PACKAGES} && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY powerapi-sampling-cpu/target/universal/stage /powerapi

WORKDIR /powerapi

ENTRYPOINT ["/powerapi/bin/sampling-cpu"]
CMD ["--all", "results/sampling", "results/processing", "results/computing"]
