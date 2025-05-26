#!/usr/bin/env bash

cd contextmapper

if [[ -n "$1" ]]; then
  mkdir -p ../output-domain
  make generate-context-map INPUT_FILE="$1"
else
  echo "Usage: $0 <input_file>"
  exit 1
fi
if [[ $? -ne 0 ]]; then
  echo "Failed to generate context map."
  exit 1
else
  echo "Context map generated successfully."
fi
