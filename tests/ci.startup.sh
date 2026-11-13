#!/bin/bash

source ./set-env.sh || exit 1

echo " == Printing the most important environment variables"
echo " MANIFEST: ${MANIFEST}"
echo " TESTS_IMAGE: ${TESTS_IMAGE}"
echo " JAHIA_IMAGE: ${JAHIA_IMAGE}"
echo " JAHIA_CLUSTER_ENABLED: ${JAHIA_CLUSTER_ENABLED}"
echo " MODULE_ID: ${MODULE_ID}"
echo " JAHIA_URL: ${JAHIA_URL}"
# Never print the super user password: only whether it is set.
if [[ -n "${SUPER_USER_PASSWORD}" ]]; then
  echo " SUPER_USER_PASSWORD: (set, not shown)"
else
  echo " SUPER_USER_PASSWORD: (not set)"
fi

version=$(node -p "require('./package.json').devDependencies['@jahia/cypress']")
echo Using @jahia/cypress@$version...
npx --yes --package @jahia/cypress@$version ci.startup
