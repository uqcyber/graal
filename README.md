# uq_test_isa

The uq_test_isa branch introduces various features to allow GraalVM IR graphs to be translated into Isabelle. The following commands assume GraalVM has already been built using mx.

## Running unit tests

Run the following to execute all unittests and dump their IR graphs

```mx unittest```

There are various command line options defined in VeriOpt.java. Use them like follows

```mx unittest -Duq.encode_float_stamps=false```

## Minecraft

Run the following script to download and prepare the Minecraft environment. Note that the script itself may not work on Windows as it is written in Bash, however it does produce an environment that can run on Windows. The environment is put into a folder called `minecraft`.

```./setup_minecraft_test.sh```

Run the following script to launch Minecraft. You can edit this script to include the same VeriOpt.java parameters as used in `mx unittest`. The dumps of optimisations are placed into the directory `minecraft/optimizations`.

```./run_minecraft-private.sh```

# GraalVM

[![GraalVM downloads][badge-dl]][downloads] [![GraalVM docs][badge-docs]][docs] [![GraalVM on Slack][badge-slack]][slack] [![GraalVM on Twitter][badge-twitter]][twitter] [![GraalVM on YouTube][badge-yt]][youtube]  [![GraalVM Gate][badge-gate]][gate] [![License][badge-license]](#license)

GraalVM is a high-performance JDK distribution designed to accelerate the execution of applications written in Java and other JVM languages along with support for JavaScript, Ruby, Python, and a number of other popular languages.

The project website at [https://www.graalvm.org/][website] describes how to [get started][getting-started], how to [stay connected][community], and how to [contribute][contributors].

## Documentation

Please refer to the [GraalVM website for documentation][docs]. You can find most of the documentation sources in the [`docs/`](docs/) directory in the same hierarchy as displayed on the website. Additional documentation including developer instructions for individual components can be found in corresponding `docs/` sub-directories. The documentation for the Truffle framework, for example, is in [`truffle/docs/`](truffle/docs/). This also applies to languages, tools, and other components maintained in [related repositories](#related-repositories).

## Get Support

* Open a [GitHub issue][issues] for bug reports, questions, or requests for enhancements.
* Join the [GraalVM Slack][slack] to connect with the community and the GraalVM team.
* Report a security vulnerability according to the [Reporting Vulnerabilities guide][reporting-vulnerabilities].

## Repository Structure

This source repository is the main repository for GraalVM and includes the following components:

Directory | Description
------------ | -------------
[`.github/`](.github/) | Configuration files for GitHub issues, workflows, ….
[`compiler/`](compiler/) | [Graal compiler][reference-compiler], a modern, versatile compiler written in Java.
[`espresso/`](espresso/) | [Espresso][java-on-truffle], a meta-circular Java bytecode interpreter for the GraalVM.
[`java-benchmarks/`](java-benchmarks/) | Java benchmarks.
[`regex/`](regex/) | TRegex, a regular expression engine for other GraalVM languages.
[`sdk/`](sdk/) | [GraalVM SDK][graalvm-sdk], long-term supported APIs of GraalVM.
[`substratevm/`](substratevm/) | Framework for ahead-of-time (AOT) compilation with [Native Image][native-image].
[`sulong/`](sulong/) | [Sulong][reference-sulong], an engine for running LLVM bitcode on GraalVM.
[`tools/`](tools/) | Tools for GraalVM languages implemented with the instrumentation framework.
[`truffle/`](truffle/) | GraalVM's [language implementation framework][truffle] for creating languages and tools.
[`vm/`](vm/) | Components for building GraalVM distributions.
[`wasm/`](wasm/) | [GraalWasm][reference-graalwasm], an engine for running WebAssembly programs on GraalVM.

## Related Repositories

GraalVM provides additional languages, tools, and other components developed in related repositories. These are:

Name         | Description
------------ | -------------
[FastR] | Implementation of the R language.
[GraalJS] | Implementation of JavaScript and Node.js.
[GraalPython] | Implementation of the Python language.
[GraalVM Demos][graalvm-demos] | Several example applications illustrating GraalVM capabilities.
[Native Build Tools][native-build-tools] | Build tool plugins for GraalVM Native Image.
[SimpleLanguage] | A simple example language built with the Truffle framework.
[SimpleTool] | A simple example tool built with the Truffle framework. 
[TruffleRuby] | Implementation of the Ruby language.
[VS Code Extensions][vscode-extensions] | VS Code extensions for GraalVM.

## License

GraalVM Community Edition is open source and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](LICENSE), which are the same terms as for Java. The licenses of the individual GraalVM components are generally derivative of the license of a particular language (see the table below). GraalVM Community is free to use for any purpose - no strings attached.

Component(s) | License
------------ | -------------
[Espresso](espresso/LICENSE) | GPL 2
[GraalVM Compiler](compiler/LICENSE.md), [SubstrateVM](substratevm/LICENSE), [Tools](tools/LICENSE), [VM](vm/LICENSE_GRAALVM_CE) | GPL 2 with Classpath Exception
[GraalVM SDK](sdk/LICENSE.md), [GraalWasm](wasm/LICENSE), [Truffle Framework](truffle/LICENSE.md), [TRegex](regex/LICENSE.md) | Universal Permissive License
[Sulong](sulong/LICENSE) | 3-clause BSD


[badge-dl]: https://img.shields.io/badge/download-latest-blue
[badge-docs]: https://img.shields.io/badge/docs-read-green
[badge-gate]: https://github.com/oracle/graal/actions/workflows/main.yml/badge.svg
[badge-license]: https://img.shields.io/badge/license-GPLv2+CE-green
[badge-slack]: https://img.shields.io/badge/Slack-join-active?logo=slack
[badge-twitter]: https://img.shields.io/badge/Twitter-@graalvm-active?logo=twitter
[badge-yt]: https://img.shields.io/badge/YouTube-subscribe-active?logo=youtube
[community]: https://www.graalvm.org/community/
[contributors]: https://www.graalvm.org/community/contributors/
[docs]: https://www.graalvm.org/docs/introduction/
[downloads]: https://www.graalvm.org/downloads/
[fastr]: https://github.com/oracle/fastr
[gate]: https://github.com/oracle/graal/actions/workflows/main.yml
[getting-started]: https://www.graalvm.org/docs/getting-started/
[graaljs]: https://github.com/oracle/graaljs
[graalpython]: https://github.com/oracle/graalpython
[graalvm-demos]: https://github.com/graalvm/graalvm-demos
[graalvm-sdk]: https://www.graalvm.org/sdk/javadoc/
[issues]: https://github.com/oracle/graal/issues
[java-on-truffle]: https://www.graalvm.org/reference-manual/java-on-truffle/
[native-build-tools]: https://github.com/graalvm/native-build-tools
[native-image]: https://www.graalvm.org/reference-manual/native-image/
[reference-compiler]: https://www.graalvm.org/reference-manual/compiler/
[reference-graalwasm]: https://www.graalvm.org/reference-manual/wasm/
[reference-sulong]: https://www.graalvm.org/reference-manual/llvm/
[reporting-vulnerabilities]: https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html
[simplelanguage]: https://github.com/graalvm/simplelanguage
[simpletool]: https://github.com/graalvm/simpletool
[slack]: https://www.graalvm.org/slack-invitation/
[truffle]: https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/
[truffleruby]: https://github.com/oracle/truffleruby
[twitter]: https://twitter.com/graalvm
[vscode-extensions]: https://github.com/graalvm/vscode-extensions
[website]: https://www.graalvm.org/
[youtube]: https://www.youtube.com/graalvm
