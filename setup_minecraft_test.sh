#/bin/bash
set -e

echo "cd minecraft; ../sdk/latest_graalvm_home/bin/java -Xmx1G -Duq.dump_optimizations=true -Djava.library.path=. -classpath \"*\" net.minecraft.client.main.Main --version 1.8 --accessToken aeef7bc935f9420eb6314dea7ad7e1e5 --userProperties {}" > run_minecraft-private.sh
chmod +x run_minecraft-private.sh

mkdir minecraft
cd minecraft

echo Downloading https://launchermeta.mojang.com/mc/game/version_manifest.json
VERSION_MANIFEST=$(curl https://launchermeta.mojang.com/mc/game/version_manifest.json)
regex="[^\"]*\/1\.8\.json"
[[ $VERSION_MANIFEST =~ $regex ]]

echo Downloading $BASH_REMATCH
VERSION_INFO=$(curl $BASH_REMATCH)

regex="(https:)[^\"]*\.jar"

downloadLibraries() {
  while [[ $1 ]]
  do
    [[ $1 =~ $regex ]] && echo Downloading "$BASH_REMATCH" && curl -O $BASH_REMATCH
    shift
  done
}

downloadLibraries $VERSION_INFO

rm server.jar

unzip -o "*-natives-*.jar"
