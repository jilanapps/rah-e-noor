#!/bin/bash
# Rah-e-Noor Play AAB auto-build (GitHub Actions se chalta hai).
# Secrets env vars se aate hain: ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
set -e

echo "=== 1. Android SDK ==="
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --install "platforms;android-36" "build-tools;36.0.0"

echo "=== 2. bundletool ==="
BT_TAG=$(curl -s https://api.github.com/repos/google/bundletool/releases/latest | python3 -c "import sys,json; print(json.load(sys.stdin)['tag_name'])")
curl -sL -o /tmp/bundletool.jar "https://github.com/google/bundletool/releases/download/${BT_TAG}/bundletool-all-${BT_TAG}.jar"
java -jar /tmp/bundletool.jar version

echo "=== 3. AdMob AARs ==="
mkdir -p /tmp/gma
curl -sSL -o /tmp/gma/ads-lite.aar "https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads-lite/23.6.0/play-services-ads-lite-23.6.0.aar"
curl -sSL -o /tmp/gma/basement.aar "https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-basement/18.4.0/play-services-basement-18.4.0.aar"
mkdir -p /tmp/gma/ads-lite /tmp/gma/basement
unzip -q -o /tmp/gma/ads-lite.aar -d /tmp/gma/ads-lite
unzip -q -o /tmp/gma/basement.aar -d /tmp/gma/basement

echo "=== 4. Web assets (assets/www) ==="
mkdir -p build/assets/www
cp index.html book-toc.js manifest.json privacy-policy.html sw.js version.json build/assets/www/
cp icon-192.png icon-512.png icon-master.png build/assets/www/
cp -r books data js build/assets/www/
rm -f build/assets/www/rah-e-noor.apk build/assets/www/rah-e-noor.aab build/assets/www/rah-e-noor-ads-test.apk
du -sh build/assets/www

echo "=== 5. Play patch (in-app updater hatayein) ==="
python3 - <<'PYEOF'
p = 'build/assets/www/index.html'
s = open(p, encoding='utf-8').read()

old_div = '''  <div class="card" id="updateCard" style="display:none;border:2px solid var(--gold);background:#fff8e1">
    <div style="font-weight:700">\U0001F389 Naya version available hai!</div>
    <div class="note">Nai features ke liye app update karein</div>
    <a href="https://jilanapps.github.io/rah-e-noor/rah-e-noor.apk" class="btn gold" style="display:inline-block;margin-top:8px;text-decoration:none">\U0001F4E5 Update Download Karein</a>
  </div>'''
assert old_div in s, 'updateCard div block nahi mila'
s = s.replace(old_div, '<!-- Play build: in-app updater removed \u2014 updates come via Google Play Store -->')

old_fn = """async function checkUpdate(){
  try{
    const r=await fetch('version.json?t='+Date.now());
    if(!r.ok) return;
    const v=await r.json();
    if(v.version && v.version!==APP_VERSION){
      $('updateCard').style.display='block';
    }
  }catch(e){}
}
setTimeout(checkUpdate,3000);"""
assert old_fn in s, 'checkUpdate function nahi mili'
s = s.replace(old_fn, """/* PLAY BUILD: disabled \u2014 updates are delivered via Google Play Store.
   (Direct-download builds keep the website-APK update checker.) */
async function checkUpdate(){ /* no-op in Play build */ }""")

assert 'updateCard' not in s, 'updateCard ka hawala baqi hai'
open(p, 'w', encoding='utf-8').write(s)
print('Play patch OK: updater removed, checkUpdate no-op')
PYEOF

echo "=== 6. versionCode / versionName ==="
VC=$((21 + GITHUB_RUN_NUMBER))
VN=$(tr -d '[:space:]' < android/versionName.txt)
sed -e "s/__VERSION_CODE__/${VC}/" -e "s/__VERSION_NAME__/${VN}/" android/AndroidManifest.xml > build/AndroidManifest.xml
grep -o 'android:versionCode="[0-9]*" android:versionName="[^"]*"' build/AndroidManifest.xml

echo "=== 7. aapt2 compile + link ==="
BT="$ANDROID_SDK_ROOT/build-tools/36.0.0"
mkdir -p build/res-merged
cp -r android/res/* build/res-merged/
cp -r /tmp/gma/ads-lite/res/* build/res-merged/
cp -r /tmp/gma/basement/res/* build/res-merged/
"$BT/aapt2" compile --dir build/res-merged -o build/compiled_res.zip
mkdir -p build/gen
"$BT/aapt2" link -o build/linked.apk \
  -I "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" \
  --manifest build/AndroidManifest.xml \
  --java build/gen \
  -A build/assets \
  build/compiled_res.zip

echo "=== 8. Java + dex (d8) ==="
AJAR="$ANDROID_SDK_ROOT/platforms/android-36/android.jar"
mkdir -p build/classes build/dex
javac -encoding UTF-8 -classpath "${AJAR}:/tmp/gma/ads-lite/classes.jar" \
  -d build/classes \
  build/gen/com/jilanapps/rahenoor/R.java \
  android/src/com/jilanapps/rahenoor/MainActivity.java
(cd build/classes && jar cf ../app-classes.jar .)
"$BT/d8" --lib "$AJAR" --output build/dex \
  build/app-classes.jar /tmp/gma/ads-lite/classes.jar /tmp/gma/basement/classes.jar

echo "=== 9. Bundle (AAB) ==="
"$BT/aapt2" convert --output-format proto -o build/proto.zip build/linked.apk
rm -rf build/module && mkdir -p build/module
(cd build/module && unzip -q ../proto.zip)
mkdir -p build/module/manifest build/module/dex
mv build/module/AndroidManifest.xml build/module/manifest/AndroidManifest.xml
cp build/dex/classes.dex build/module/dex/
rm -f build/base.zip
(cd build/module && zip -qr ../base.zip .)
java -jar /tmp/bundletool.jar build-bundle --modules=build/base.zip --output=build/unsigned.aab

echo "=== 10. Sign ==="
echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > /tmp/release.keystore
jarsigner -keystore /tmp/release.keystore \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -signedjar build/rah-e-noor.aab build/unsigned.aab "$KEY_ALIAS"
rm -f /tmp/release.keystore
jarsigner -verify build/rah-e-noor.aab

echo "=== 11. Verify ==="
java -jar /tmp/bundletool.jar build-apks --bundle=build/rah-e-noor.aab --output=/tmp/verify.apks --mode=universal
unzip -p /tmp/verify.apks universal.apk > /tmp/universal.apk
"$BT/aapt" dump badging /tmp/universal.apk | grep -E "^package:|^targetSdkVersion:"
if "$BT/aapt" dump badging /tmp/universal.apk | grep -q "REQUEST_INSTALL_PACKAGES"; then
  echo "FAIL: REQUEST_INSTALL_PACKAGES maujood hai"
  exit 1
fi
echo "OK: REQUEST_INSTALL_PACKAGES nahi hai"
ls -la build/rah-e-noor.aab
echo "BUILD SUCCESS: build/rah-e-noor.aab"
