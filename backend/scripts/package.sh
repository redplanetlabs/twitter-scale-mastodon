#!/bin/bash

mvn package -Dmaven.test.skip=true "${@}"
