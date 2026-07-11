# Set error action
$ErrorActionPreference = "Stop"

# Go to TLS directory
Set-Location "c:\Users\VLT14\Documents\UNI\PISSIR\Progetto\gamehandler-platform\infrastructure\tls"

Write-Output "--- Generating Central CA ---"
openssl genrsa -out central-ca.key 2048
openssl req -x509 -new -nodes -key central-ca.key -sha256 -days 3650 -out central-ca.crt -subj "/CN=Central Root CA/O=GamePlatformCentral/C=IT"

Write-Output "--- Generating Central System HTTPS Server Key and Certificate ---"
openssl genrsa -out central-system-https.key 2048
openssl req -new -key central-system-https.key -out central-system-https.csr -subj "/CN=central-system/O=GamePlatformCentral/C=IT"

# Create SAN ext file for Central System
@'
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names
[alt_names]
DNS.1 = central-system
DNS.2 = localhost
IP.1 = 127.0.0.1
'@ | Out-File -FilePath central-system-https.ext -Encoding ascii

openssl x509 -req -in central-system-https.csr -CA central-ca.crt -CAkey central-ca.key -CAcreateserial -out central-system-https.crt -days 730 -sha256 -extfile central-system-https.ext

# Create Central System Keystore
openssl pkcs12 -export -in central-system-https.crt -inkey central-system-https.key -out central-system-https.p12 -name central-system-https -password pass:changeit

Write-Output "--- Generating Local Server HTTPS Server Key and Certificate ---"
openssl genrsa -out local-server-https.key 2048
openssl req -new -key local-server-https.key -out local-server-https.csr -subj "/CN=local-server-1/O=GamePlatformLocal/C=IT"

# Create SAN ext file for Local Server
@'
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names
[alt_names]
DNS.1 = local-server-1
DNS.2 = local-server-2
DNS.3 = local-server-3
DNS.4 = localhost
IP.1 = 127.0.0.1
'@ | Out-File -FilePath local-server-https.ext -Encoding ascii

# Sign with existing Local CA from Mosquitto folder
openssl x509 -req -in local-server-https.csr -CA ..\mosquitto\certs\ca.crt -CAkey ..\mosquitto\certs\ca.key -CAcreateserial -out local-server-https.crt -days 730 -sha256 -extfile local-server-https.ext

# Create Local Server Keystore
openssl pkcs12 -export -in local-server-https.crt -inkey local-server-https.key -out local-server-https.p12 -name local-server-https -password pass:changeit

Write-Output "--- Generating Truststores ---"
# Central Truststore (contains central-ca.crt)
if (Test-Path central-truststore.p12) { Remove-Item central-truststore.p12 }
keytool -importcert -trustcacerts -file central-ca.crt -alias central-ca -keystore central-truststore.p12 -storetype PKCS12 -storepass changeit -noprompt

# Local Truststore (contains local-ca.crt)
if (Test-Path local-truststore.p12) { Remove-Item local-truststore.p12 }
keytool -importcert -trustcacerts -file ..\mosquitto\certs\ca.crt -alias local-ca -keystore local-truststore.p12 -storetype PKCS12 -storepass changeit -noprompt

Write-Output "--- Done! ---"
