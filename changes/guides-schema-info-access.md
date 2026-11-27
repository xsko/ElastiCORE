### Documentation (rule guide - `GET /schema/info` and `@access`, issue 373)

#### The rule guide says that `@access` does not apply to `GET /schema/info`, and how to close it
- Why: the rule guide said that `@access` "reaches every entry point". `GET /schema/info` is answered by the
  generated controller itself (`DTOUtils.getMetaInfo(XxxDTO.class)` in `v3/control.tmpl` and `kt/control.tmpl`), not
  by the CoreService, so anyone who reaches the controller gets the field list of an entity with `read=admin`. The
  owner decided to keep the endpoint's access as it is and to document it (issue 373).
- Added: rule guide, "Access, owner and password" (English and Korean): the exception, a Spring Security rule
  (`requestMatchers(HttpMethod.GET, "/api/*/*/schema/info")`; Spring's path patterns refuse `/api/**/schema/info`), or
  `@expose(..., schema=false)`; `/v3/api-docs` needs the same rule.
- Checked: the two controller templates on this branch; the path patterns on spring-web 6.2.19 and 7.0.10 (issue 373,
  step 2). No DSL, code or generated output changed. The guide tests (`--tests "*Guide*"`).
- Files: `elasticore-rule-guide.md` and its `ko/` copy.
