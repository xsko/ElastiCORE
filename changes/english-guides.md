### Changes (guides - English by default)

#### The guides a project receives are in English; `guides = 'ko'` gives the Korean ones
- Why: ElastiCORE is an open-source project for developers who mostly do not read Korean, and the guides
  copied into every project's `blueprint/` were in Korean only. Coding agents reading them also wrote the
  code they added by hand in Korean.
- Before: every generation copied the Korean guides.
- After: the nine guides are written in English (`elasticore-guide/`) and in Korean (`elasticore-guide/ko/`),
  line for line. A generation copies the English ones under the same file names, with the index beside them;
  `elasticore { guides = 'ko' }` (`-Delasticore.guides=ko` without the plugin) copies the Korean ones. A
  language the guides are not written in gives a warning and the English guides. The env guide says so in
  12-1; README, CONTRIBUTING and the Korean documents link to the guides of their language. Change fragments
  are written in English from now on (`changes/README.md`); the history before stays in Korean.
- Generated code: unchanged - only the guides copied beside the model differ.
- Checked: `GuideTranslationTest` - each guide has the same lines, headings with their numbers, and code
  blocks of the same length in both languages, and the English prose holds no Korean;
  `GuideCoverageTest` runs on the English guides and, as `GuideCoverageKoTest`, on the Korean ones;
  `GuideIndexTest.theLanguageIsChosen` deploys the default, `ko` and an unknown language;
  `ElastiCorePluginTest.theGuidesLanguageIsPassed`.
- Files: `GuideDeployer`, `GuideIndex`, `ElastiCoreExtension`, `ElastiCorePlugin`,
  `elasticore-guide/*.md`, `elasticore-guide/ko/*.md`, `README.md`, `README.ko.md`, `CONTRIBUTING.md`,
  `changes/README.md`, `docs/*.ko.md`.
