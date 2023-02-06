# Veriopt Fuzz: fuzzing the Graal compiler with JQF-Zest

This branch contains the effort to implement fuzzing as a form of differential testing on the Graal compiler. It 
utilises the novel [JQF framework](https://github.com/rohanpadhye/JQF)\[1\].

Technically, the Graal compiler tests already perform differential testing of sorts, by fetching pre-defined
code snippets, running them through the JVM, then compiling them through Graal and comparing the results. What
differentiates my efforts is that the existing test code consists of pre-defined, hard coded methods or code
generation sequences, whereas the fuzz tests generate randomized code.

## Getting it working

As it currently stands, my implementation can be described as "one massive hack". Most of the dependencies are
included in `suite.py`, however some additional work is required:

- Some modifications to `mx` need to be done, in particular upgrading Junit from 4.12 to 4.13.1, since this is
required for JQF to work. In the `suite.py` for `mx` (**not** the compiler one) navigate to the Junit definition in
the libraries section and make the following changes:
```python
"JUNIT" : {
      "digest": "sha1:cdd00374f1fee76b11e2a9d127405aa3f6be5b6a",
      "sourceDigest": "sha1:523bdce923a13622132b263550d06519fc591ec4",
      "dependencies" : ["HAMCREST"],
      "licence" : "EPL-2.0",
      "maven" : {
        "groupId" : "junit",
        "artifactId" : "junit",
        "version" : "4.13.1",
      }
},
```
- To run the tests, I created a script called `invokeGraalFuzz.sh` to bypass `mx unittest`. Unfortunately as it
stands it is tailored to my workstation and thus will need to be changed to work on othe machines. In the meantime
you can try these steps to create your own script:

1. Save the old script (to something like `invokeGraalFuzz.sh.old`). then get a verbose output from `mx unittest`
and pipe it to a file (`mxUnittest.out` in this example). This should contain the exact command to Java:
```
$ mx -V unittest <testname> > mxUnittest.out 2> mxUnittest.err
```
Then get the very large Java command from the output file (i.e. `mxUnittest.out`) and put it in a new
`invokeGraalFuzz.sh`.

2. Put in the flags to set up JQF's instrumentation agent. In my script I put them in between the args
`-Djava.awt.headless=true` and `-cp ...` (replace `<digest>` with the exact SHA digest that is specified in the
suite file):
```
-Xbootclasspath/a:/path/to/.mx/cache/JQF_INSTRUMENT_<digest>/jqf-instrument.jar:/path/to/.mx/cache/ASM_9.1_<digest>/asm-9.1.jar \
-javaagent:/path/to/.mx/cache/JQF_INSTRUMENT_<digest>/jqf-instrument.jar \
-Djanala.conf=/path/to/graal/compiler/janala.conf
```

3. Add the flags to set up the module exports and opens. In between the second
`--add-exports=java.base/jdk.internal.module=ALL-UNNAMED` (which should be the one following the argument 
`-Dpolyglot.engine.WarnInterpreterOnly=false`) and up until the `-Dtruffle.nfi.library=...` argument,
replace everything in between with this:
```
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-modules=ALL-MODULE-PATH @jvmci_exports @graal_exports \
--add-opens=jdk.internal.vm.compiler.management/org.graalvm.compiler.management=ALL-UNNAMED \
--add-opens=jdk.internal.vm.compiler.truffle.jfr/org.graalvm.compiler.truffle.jfr.impl=ALL-UNNAMED \
 @truffle_api_exports @graal_sdk_exports \
```

4. Find where it says `com.oracle.mxtool.junit.MxJunitWrapper` and replace it with 
`edu.berkeley.cs.jqf.fuzz.ei.ZestDriver`. Delete everything that follows this, in particular all the `-JUnit*`
arguments as they are specific to the `mx` wrapper. Then specify the test class after `ZestDriver` followed by the
test method. It should look like this:
```
... edu.berkeley.cs.jqf.fuzz.ei.ZestDriver <testClass> <testMethod>
```

This should be enough to get it running. If you're unsure or stuck then refer to the given `invokeGraalFuzz.sh`
script as an example. In future the ideal way would be to modify `mx`, perhaps to add a new
command called `fuzz`, that handles this for us.

Al H.

## References
\[1\]R. Padhye, C. Lemieux, and K. Sen, “JQF: coverage-guided property-based testing in Java,” in *Proceedings of the 28th ACM SIGSOFT International Symposium on Software Testing and Analysis*, Beijing China, Jul. 2019, pp. 398–401. doi: [10.1145/3293882.3339002](https://doi.org/10.1145/3293882.3339002).
