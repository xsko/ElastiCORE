### Bug Fixes (2.x generator - how a failed generation ends)

#### A failed 2.x generation ends non-zero, with its cause
- Why: `ModelExtractor.main` - what `./gradlew elcore`, `output = 'v2'` and `-Pv2` run - caught every exception,
  printed it and ended 0, so a generation that failed let the build go on to compile what was there. Its check
  `e.getMessage().contains(...)` threw a second exception for one without a message, which hid the cause, and the
  cast after it would have thrown a `ClassCastException` for any other exception naming "Not found model DSL file".
  Two failures were not even exceptions there: a model file that is not YAML (`LOAD ERROR`, nothing it declares
  generated - elcore3 already stopped on it) and a source that could not be written were printed and the run ended 0.
- Now: an exception ends the run 1 after its stack trace and `[ERROR] ElastiCORE generation failed: <the exception>`;
  a model file not read and a source not written are counted (as elcore3 counts the first) and end it 1 with
  `[ERROR] LOAD ERROR <file> - not read, nothing it declares was generated: <parser message>` or
  `[ERROR] not written: <class> - <exception>`. The init path (no model DSL yet) still ends 0. Generated code does
  not change; a 2.x project whose generation fails now fails its build - which is the point.
- Checked: `GenerationExitTest` runs the extractor's `main` in its own JVM - a `List<>` field (the 2.x generator throws
  `IllegalArgumentException: toName is null`), a model file that is not YAML and a source root that is a file each
  end 1 with the cause, a model that generates ends 0; they fail without the change (ended 0). With the plugin
  (`output = 'v2'`, `elasticore-template`): `gradlew :elasticore-project:elasticore-template:elcore` passed as
  before, and with a `probeBroken: List<>` line added it failed - `[ERROR] ElastiCORE generation failed:
  java.lang.IllegalArgumentException: toName is null`, `finished with non-zero exit value 1` (probe not committed).
- Files: `extract/ModelExtractor.java`, `pub/jpa/SrcFilePublisher.java`, `GenerationExitTest.java`.