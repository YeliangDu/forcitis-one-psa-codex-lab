FROM postgres@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42
COPY . /opt/lab54-input/
RUN cd /opt/lab54-input && sha256sum --check SHA256SUMS && \
    printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d && \
    chmod 755 /usr/sbin/policy-rc.d && \
    rm -rf /var/lib/apt/lists/* && \
    cp -a payload/lists/. /var/lib/apt/lists/ && \
    cp payload/debs/*.deb /var/cache/apt/archives/ && \
    /bin/bash -ceu 'set --; while IFS= read -r target; do set -- "$@" "$target"; done < payload/targets.txt; \
      DEBIAN_FRONTEND=noninteractive apt-get -o APT::Get::AllowUnauthenticated=false \
      -y --no-download --no-install-recommends install "$@"' && \
    mkdir /opt/lab54-jdk && \
    tar -xzf payload/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz \
      --no-same-owner --strip-components=1 -C /opt/lab54-jdk && \
    ln -s /opt/lab54-jdk/bin/java /usr/bin/java && \
    ln -s /opt/lab54-jdk/bin/javac /usr/bin/javac && \
    /bin/bash -ceu 'for name in forcitis forcitis-migrate nginx; do \
      if ! id "$name" >/dev/null 2>&1; then \
        useradd --system --user-group --no-create-home --shell /usr/sbin/nologin "$name"; \
      fi; test "$(id -u "$name")" -ne 0; done' && \
    install -m 755 entrypoint.sh /usr/local/bin/lab54-capability
ENTRYPOINT ["/usr/local/bin/lab54-capability"]
