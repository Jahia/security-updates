#!/bin/bash
# Exports the variables of .env (the maintainer's values) or, when it is missing, of .env.example.

if [[ -f .env ]]; then
  ENV_FILE=.env
elif [[ -f .env.example ]]; then
  ENV_FILE=.env.example
else
  echo "set-env.sh: neither tests/.env nor tests/.env.example exists in $(pwd)." >&2
  echo "set-env.sh: create tests/.env from tests/.env.example (JAHIA_IMAGE, JAHIA_LICENSE, SUPER_USER_PASSWORD, ...)." >&2
  # Sourced by the CI scripts: return to the caller when sourced, exit when run directly.
  return 1 2>/dev/null || exit 1
fi

source "${ENV_FILE}"
export $(sed 's/=.*//g' "${ENV_FILE}" | xargs)
