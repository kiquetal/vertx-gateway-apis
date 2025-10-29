#!/bin/sh
#
# This script's only purpose is to execute the command passed to it.
# This allows for a flexible startup, where this script
# *could* be expanded later to wait for a database or
# calculate memory settings, without changing the Dockerfile.
#
# The 'exec' command replaces this script's process with the 'java'
# process, which is the correct way to run a service in Docker.
#
exec "$@"
