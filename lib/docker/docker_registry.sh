#!/bin/bash
# Starts up a local Docker registry based on the setting in the docker_registry_config.yml

docker run \
         -e SETTINGS_FLAVOR=dev \
         -e SEARCH_BACKEND=sqlalchemy \
         -p 5000:5000 \
         registry
