# Veriopt Fuzz: fuzzing the Graal compiler with JQF-Zest

This branch contains the effort to implement fuzzing as a form of differential testing on the Graal compiler. It 
utilises the novel [JQF framework](https://github.com/rohanpadhye/JQF)\[1\].

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
stands it is tailored to my workstation and thus will need to be changed to work on othe machines.

## References
\[1\]R. Padhye, C. Lemieux, and K. Sen, “JQF: coverage-guided property-based testing in Java,” in *Proceedings of the 28th ACM SIGSOFT International Symposium on Software Testing and Analysis*, Beijing China, Jul. 2019, pp. 398–401. doi: [10.1145/3293882.3339002](https://doi.org/10.1145/3293882.3339002).
