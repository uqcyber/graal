# I am not brave enough to find a way to get mx to build scala
# this script is a horrible hack to simulate it on my pc for now

mkdir -p ./src/org.graalvm.veriopt.integration/src/org/graalvm/veriopt/integration
cp ../compiler/src/org.graalvm.compiler.phases.common/src/org/graalvm/compiler/phases/common/VerioptIntegration.scala ./src/org.graalvm.veriopt.integration/src/org/graalvm/veriopt/integration/VerioptIntegration.scala

scalac -classpath ../compiler/mxbuild/dists/jdk1.8/*:/Library/Java/JavaVirtualMachines/openjdk1.8.0_262-jvmci-20.3-b02/Contents/Home/jre/lib/jvmci/jvmci-api.jar\
 ./src/org.graalvm.veriopt.integration/src/org/graalvm/veriopt/integration/VerioptIntegration.scala 

rm -rf mxbuild
mkdir -p mxbuild/dists
mv org mxbuild
jar cvf ./mxbuild/dists/veriopt-integration.jar -C mxbuild .

zip -r ./mxbuild/dists/veriopt-integration.src.zip ./src/*

cp /usr/local/Cellar/scala/2.13.6/libexec/lib/scala-library.jar ./mxbuild/dists