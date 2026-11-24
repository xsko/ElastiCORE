### Bug Fixes (v3 - TypeScript client, build-time warnings)

#### `commands:` the server does not serve: not offered by the TypeScript client, and said at build time
- Why: the TypeScript client wrote a function per `commands:` entry (`close(id)` posting to `/{id}/close`) for every
  entity with a single key, while the server writes and serves a command only for a root with a behaviour class - a
  root that is not `@editable`, with `architecture = 'ddd'` or `'hexagonal'`. In layered, and for an `@editable` root,
  the client called an endpoint that does not exist (404). A layered build also ignored `commands:` without a word.
- Now: the client offers the command functions only where the server writes them, the same test `DddCommandRules`
  makes. A layered domain declaring `commands:` gets `[...] Ticket declares commands: (ddd-command), which the selected
  rules do not enforce ... The jpa-springboot-v3 bundle does not carry ddd-command, which writes a command in the
  root's behaviour class and serves it as POST /{id}/<command> - a root that is not @editable, with architecture =
  'ddd' or 'hexagonal'. The commands are not served, and the TypeScript client offers none.` (`ddd` and `hexagonal`
  already said it for an `@editable` root). The generated server code does not change; the client of a layered or
  `@editable` root loses functions that could only fail.
- Checked: `IndexColumnsTest.commandsTheServerDoesNotServeAreNotOffered` - layered Java (warning, no `close` in
  `TicketApi.ts`), hexagonal (no warning, `'/close'` offered).
- Files: `TypeScriptApiRules` (`serves`), `RulesGenRunner` (`warnUnenforced`, `unenforcedWarning`).
