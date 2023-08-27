#!/usr/bin/env bash
set -eo pipefail

hash thrift 2> /dev/null ||
    {
        printf 'thrift not found.  Install with `brew install thrift`\n';
        exit 1
    }

rm -rf src/main/java/com/rpl/mastodon/data
thrift --out src/main/java --gen java:generated_annotations=suppress src/mastodon.thrift
