#!/usr/bin/env bash

cd contextmapper

if [[ -n "$1" ]]; then
  make validate INPUT_FILE="$1"
else
  make validate-all
fi
