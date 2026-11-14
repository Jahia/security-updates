#!/bin/sh
# TEST-ONLY material for the fixture store of the e2e tests. Run once per environment by the "storefixture-tls"
# service of docker-compose.yml, in the Jahia image (it has openssl and the JDK the Jahia server runs on).
#
# It writes into the shared volume mounted at /srv/storefixture:
#   tls/storefixture.crt, tls/storefixture.key  a self-signed certificate for the host name "storefixture" (SAN),
#                                               valid 30 days, created here and never committed
#   trust/truststore.p12                        the JDK cacerts of the Jahia image plus that certificate, given to
#                                               the Jahia JVM with -Djavax.net.ssl.trustStore (CATALINA_OPTS)
#   https/, http-trap/                          the files nginx serves over https (contents/, and stores/ for the
#                                               failing stores) and, as a trap, over http, copied with
#                                               permissions nginx can read whatever the host umask
#
# The module only fetches https stores: the fixture store must be a real https server that Jahia trusts, the way a
# customer's private store is. Nothing here is a secret: the key only serves a throwaway test container.
#
# Idempotent: docker compose runs this service again on each "up" (ci.startup.sh runs two), possibly while Jahia
# starts; existing, still valid material is kept so that nginx and the Jahia JVM keep matching certificates.
set -eu

SRC=/storefixture-src
OUT=/srv/storefixture
CERT="$OUT/tls/storefixture.crt"
KEY="$OUT/tls/storefixture.key"
TRUST="$OUT/trust/truststore.p12"
# The JDK's own cacerts password: the truststore holds public certificates only.
STOREPASS=changeit
ONE_DAY_SECONDS=86400

if [ -s "$CERT" ] && [ -s "$KEY" ] && [ -s "$TRUST" ] && openssl x509 -checkend "$ONE_DAY_SECONDS" -noout -in "$CERT" >/dev/null; then
    echo "storefixture-tls: keeping the existing test certificate and truststore"
else
    echo "storefixture-tls: creating a TEST-ONLY certificate for storefixture and the Jahia truststore"
    rm -rf "$OUT/tls" "$OUT/trust"
    mkdir -p "$OUT/tls" "$OUT/trust"
    openssl req -x509 -newkey rsa:2048 -sha256 -days 30 -nodes \
        -keyout "$KEY" -out "$CERT" \
        -subj "/CN=storefixture" \
        -addext "subjectAltName=DNS:storefixture" \
        -addext "extendedKeyUsage=serverAuth" 2>/dev/null
    cp "$JAVA_HOME/lib/security/cacerts" "$TRUST.tmp"
    keytool -importcert -noprompt -alias storefixture-test-only -file "$CERT" \
        -keystore "$TRUST.tmp" -storetype PKCS12 -storepass "$STOREPASS" >/dev/null
    mv "$TRUST.tmp" "$TRUST"
    # nginx's master process (root) reads the key; the Jahia JVM (tomcat) reads the truststore.
    chmod 600 "$KEY"
    chmod 644 "$CERT" "$TRUST"
    chmod 755 "$OUT/tls" "$OUT/trust"
fi

mkdir -p "$OUT/https" "$OUT/http-trap"
cp -R "$SRC/contents" "$OUT/https/"
# the feeds of the failing stores of 08-securityUpdates-StoreErrors.cy.ts (stores/<name>/contents/, see nginx.conf)
cp -R "$SRC/stores/." "$OUT/https/"
cp -R "$SRC/http-trap/contents" "$OUT/http-trap/"
chmod 755 "$OUT"
find "$OUT/https" "$OUT/http-trap" -type d -exec chmod 755 {} +
find "$OUT/https" "$OUT/http-trap" -type f -exec chmod 644 {} +
echo "storefixture-tls: done"
