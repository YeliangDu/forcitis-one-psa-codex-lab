# shellcheck shell=bash
# Shared by build.sh, verify_bundle.sh and the workflows; sourced, not run.

RELEASE_VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$'
RELEASE_APPLICATION_CLASS='com.forcitis.one.OneApplication'

release_version_valid() {
  printf '%s' "${1:-}" | grep -Eq "$RELEASE_VERSION_PATTERN"
}

release_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

release_sha256_check() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum --quiet -c "$1"
  else
    shasum -a 256 --quiet -c "$1"
  fi
}

# A Boot jar whose Start-Class is the application and whose application class declares main(); anything else cannot start under systemd.
release_check_jar_runnable() {
  local jar="$1" work="$2" start_class
  mkdir -p "$work"
  start_class="$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | awk '/^Start-Class:/{c=$2; next} /^ /{if (c!="") c=c substr($0,2); next} {if (c!="") {print c; exit}}')"
  # JarLauncher (JAR layout) or PropertiesLauncher (ZIP layout, which also lets operators start other mains with -Dloader.main)
  if ! unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | grep -Eq '^Main-Class: org\.springframework\.boot\.loader\.launch\.(JarLauncher|PropertiesLauncher)$'; then
    echo "jar check: $jar is not a Spring Boot executable jar" >&2
    return 1
  fi
  if [ "$start_class" != "$RELEASE_APPLICATION_CLASS" ]; then
    echo "jar check: Start-Class is '$start_class', expected $RELEASE_APPLICATION_CLASS" >&2
    return 1
  fi
  local class_path="BOOT-INF/classes/${RELEASE_APPLICATION_CLASS//.//}.class"
  (cd "$work" && unzip -q -o "$jar" "$class_path") || { echo "jar check: $class_path missing" >&2; return 1; }
  if ! javap -p -cp "$work/BOOT-INF/classes" "$RELEASE_APPLICATION_CLASS" | grep -Eq 'static void main\(java\.lang\.String(\[\]|\.\.\.)\)'; then
    echo "jar check: $RELEASE_APPLICATION_CLASS has no static main(String[]); java -jar cannot start the application" >&2
    return 1
  fi
}

# The jar must carry the release version in both build-info and the manifest, so a running instance reports what was deployed.
release_check_jar_version() {
  local jar="$1" version="$2" built manifest
  built="$(unzip -p "$jar" META-INF/build-info.properties 2>/dev/null | tr -d '\r' | sed -n 's/^build\.version=//p')"
  if [ "$built" != "$version" ]; then
    echo "jar check: build-info version is '${built}', expected ${version}" >&2
    return 1
  fi
  manifest="$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Implementation-Version: //p')"
  if [ "$manifest" != "$version" ]; then
    echo "jar check: manifest Implementation-Version is '${manifest}', expected ${version}" >&2
    return 1
  fi
}

# Deterministic-enough archive: fixed owner and mtime, no macOS metadata, entries sorted by the file walk.
release_pack() {
  local parent="$1" name="$2" archive="$3" epoch="$4"
  find "$parent/$name" -exec touch -h -d "@$epoch" {} + 2>/dev/null \
    || TZ=UTC find "$parent/$name" -exec touch -h -t "$(TZ=UTC date -r "$epoch" +%Y%m%d%H%M.%S)" {} +
  if tar --version 2>/dev/null | grep -q bsdtar; then
    COPYFILE_DISABLE=1 tar --no-mac-metadata --no-xattrs --no-acls --uid 0 --gid 0 --uname root --gname root \
      -czf "$archive" -C "$parent" "$name"
  else
    tar --sort=name --owner=0 --group=0 --numeric-owner -czf "$archive" -C "$parent" "$name"
  fi
}
