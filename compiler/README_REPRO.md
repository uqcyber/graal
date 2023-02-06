# Fuzz repro: using JQF's reproduction goal

In addition to fuzzing, the [JQF framework](https://github.com/rohanpadhye/JQF)\[1\] also provides a mechanism
to 'reproduce' a test case from a given input that was generated from the fuzz run. It's main purpose is to
look at inputs that cause failures to determine what was the cause of the failure, but it can also be used to
look at succeeding inputs too in order to get information about which classes were instrumented and coverage
information. Just like with fuzzing, I created a crude script to do just that called `invokeGraalRepro.sh`.

## Getting it working
The repro script is pretty much a copy of the fuzz script, but with a few modifications. Just like with the fuzz
script the repro script I have provided has paths and other things that are specific to my workstation and I have
only included it for example purposes. To create a new repro script:

1. Save the old script to something like `invokeGraalRepro.sh.old`. Then copy the fuzz script that should have
been created according to the instructions in README\_FUZZ.md to a new `invokeGraalRepro.sh`.

2. Append JaCoCo jars to the end of the classpath. At the time of writing `mx` provides JaCoCo 0.8.9.202211161124,
which seems to work for JQF (even though Maven says it requires JaCoCo 0.8.7). In other words append something
like this to the classpath (replacing `<digest>` with the actual SHA digest that is specified in `mx.mx/suite.py`):
```
-cp ...<everything else>...:/path/to/.mx/cache/JACOCOCORE_0.8.9.202211161124_<digest>/jacococore-0.8.9.202211161124.jar:/path/to/.mx/cache/JACOCOREPORT_0.8.9.202211161124_<digest>/jacocoreport-0.8.9.202211161124.jar
```

3. Just after the argument `-Dpolyglot.engine.AllowExperimentalOptions=true` add the following arguments:
```
-Djqf.logCoverage=true -Djanala.verbose=true -Djqf.repro.logUniqueBranches=true
```

4. Replace `ZestDriver` with `ReproDriver`, and at the very end either add the location to a specific fuzz input
(that was generated from a fuzz run and saved in `fuzz-results/corpus`) or add a `$@` so you can just specify
any fuzz input from a fuzz run when invoking the script, for example:
```
$ ./invokeGraalRepro.sh ./fuzz-results/corpus/id_000000
```

Note that the output produced is very verbose, so it is advised to pipe it to a file so it can be examined later.

Al H.

## References
\[1\]R. Padhye, C. Lemieux, and K. Sen, “JQF: coverage-guided property-based testing in Java,” in *Proceedings of the 28th ACM SIGSOFT International Symposium on Software Testing and Analysis*, Beijing China, Jul. 2019, pp. 398–401. doi: [10.1145/3293882.3339002](https://doi.org/10.1145/3293882.3339002).
