#!/bin/bash
# Setup script for Claude Code cloud sessions (claude.ai/code > environment
# settings > "Setup script"). Paste this file's contents there; it runs before
# the repo is available, so it can't be called from the checkout.
#
# Installs the Android SDK so cloud sessions can run ./gradlew builds and unit
# tests. The image already has OpenJDK 21 and Gradle.
#
# The environment also needs:
#   - Environment variable: ANDROID_HOME=/opt/android-sdk
#   - Network access: Custom, with dl.google.com in Allowed domains and
#     "Also include default list of common package managers" checked
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if ! mkdir -p "$SDK" 2>/dev/null; then
  sudo mkdir -p "$SDK"
  sudo chown "$(id -u):$(id -g)" "$SDK"
fi

if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdline-tools.zip" \
    https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# Accepting licenses also lets the Android Gradle Plugin download any other
# SDK package it needs during a build.
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-36" "build-tools;35.0.0"

echo "Android SDK ready at $SDK"
