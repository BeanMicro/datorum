# syntax=docker/dockerfile:1
#
# Builds the datorum-server binaries. The build context must include the git
# submodules (QualityEngineering/cucumber-rs and .../goose): the workspace
# manifest lists them as members, so cargo cannot load it without them, even
# when building a single package. Clone with --recurse-submodules, or run
# `git submodule update --init --recursive` first.

FROM rust:1-slim-bookworm AS builder

# aws-lc-rs (pulled in by pgwire) builds C code via cmake and bindgen.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        clang \
        cmake \
        libclang-dev \
        perl \
        pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY . .

# Only datorum-server is needed; this skips rusqlite and the vendored
# goose/cucumber-rs crates entirely.
RUN cargo build --release -p datorum-server

# Mint a throwaway self-signed certificate at build time. The h3 server can also
# generate one itself when --cert/--key are omitted, but baking it into the image
# keeps the certificate stable across container restarts. The server expects DER,
# not PEM: a DER certificate and a PKCS#8 DER private key.
RUN openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -subj "/CN=localhost" -keyout /tmp/key.pem -out /tmp/cert.pem \
    && mkdir -p /certs \
    && openssl x509 -in /tmp/cert.pem -outform DER -out /certs/server.cert \
    && openssl pkcs8 -topk8 -nocrypt -in /tmp/key.pem -outform DER \
        -out /certs/server.key \
    && rm /tmp/key.pem /tmp/cert.pem


FROM debian:bookworm-slim AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 datorum

COPY --from=builder /build/target/release/server         /usr/local/bin/datorum-h3
COPY --from=builder /build/target/release/datorum-server /usr/local/bin/datorum-server
COPY --from=builder /build/target/release/sample         /usr/local/bin/datorum-pgwire
COPY --from=builder --chown=datorum:datorum /certs /etc/datorum/certs

USER datorum

# QUIC is UDP. 5433 is the PostgreSQL wire demo, if you run datorum-pgwire.
EXPOSE 4433/udp
EXPOSE 5433/tcp

# The h3 server is the only binary that currently listens on anything;
# datorum-server still has a `TODO Start HTTP/3 server task` where the
# listener belongs. Override with `docker run ... datorum-server`.
ENTRYPOINT ["/usr/local/bin/datorum-h3"]
CMD ["--listen", "0.0.0.0:4433", \
     "--cert", "/etc/datorum/certs/server.cert", \
     "--key",  "/etc/datorum/certs/server.key"]
