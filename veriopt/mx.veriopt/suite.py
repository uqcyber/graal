suite = {
  "mxversion" : "5.273.0",
  "name" : "veriopt",
  "sourceinprojectwhitelist" : [],

  "groupId" : "org.graalvm.veriopt",
  "version" : "0.0.1",
  "release" : False,
  "url" : "https://veriopt.uqcloud.net/",
  "developer" : {
    "name" : "Veriopt",
    "email" : "b.webb@uq.edu.au",
    "organization" : "The University of Queensland",
    "organizationUrl" : "https://uq.edu.au/",
  },
#   "scm" : {
#     "url" : "https://github.com/oracle/graal",
#     "read" : "https://github.com/oracle/graal.git",
#     "write" : "git@github.com:oracle/graal.git",
#   },

  "distributions": {
    "VERIOPT_INTEGRATION" : {
    #    "native" : True,
    #    "platformDependent" : True,
       "output" : "<mxbuild>",
    #    "dependencies" : [
    #      "com.oracle.truffle.nfi.test.native",
    #    ],
    #    "testDistribution" : True,
      "maven" : False,
     },
     "SCALA_LIBRARY" : {
     },
  }
}