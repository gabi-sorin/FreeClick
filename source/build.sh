#!/bin/bash
# Offline APK build without Gradle: aapt -> javac -> dx -> zipalign -> apksigner.
set -e
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS
JAR=${ANDROID_JAR:-/home/claude/dl/android-34.jar}
OUT=build/apk
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes

python3 tools/gen_docs.py
[ -f res/mipmap-mdpi/ic_launcher.png ] || python3 tools/make_icon.py res

echo "[1/5] resources"
aapt package -f -m -J $OUT/gen -M AndroidManifest.xml -S res -I "$JAR" -F $OUT/resources.ap_

echo "[2/5] javac"
if ! javac -Xlint:-options -Xlint:unchecked -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$JAR" -d $OUT/classes $(find src $OUT/gen -name '*.java') > $OUT/javac.log 2>&1; then
  cat $OUT/javac.log
  echo "BUILD FAILED (javac)"
  exit 1
fi
grep -v "^Note:" $OUT/javac.log || true

echo "[3/5] dex"
dalvik-exchange --dex --min-sdk-version=24 --output=$OUT/classes.dex $OUT/classes

echo "[4/5] package"
cp $OUT/resources.ap_ $OUT/unsigned.apk
(cd $OUT && zip -q unsigned.apk classes.dex)
zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[5/5] sign"
KS=keystore/freeclick.jks
if [ ! -f $KS ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore $KS -storepass freeclick -keypass freeclick -alias freeclick \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FreeClick" >/dev/null 2>&1
fi
apksigner sign --ks $KS --ks-pass pass:freeclick --key-pass pass:freeclick --out $OUT/FreeClick.apk $OUT/aligned.apk
apksigner verify $OUT/FreeClick.apk
# Updates only install over the old version (keeping all data) if the signing key never changes.
CERT=$(apksigner verify --print-certs $OUT/FreeClick.apk 2>/dev/null | grep -m1 "SHA-256 digest" | awk '{print $NF}')
if [ -f keystore/cert.sha256 ]; then
  if [ "$CERT" != "$(cat keystore/cert.sha256)" ]; then
    echo "SIGNING KEY CHANGED ($CERT) - an update would not install over the previous version!"; exit 1
  fi
  echo "signing key OK (same as previous versions)"
else
  echo "$CERT" > keystore/cert.sha256
fi
ls -la $OUT/FreeClick.apk
