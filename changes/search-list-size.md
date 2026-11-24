### Bug Fixes (v3 - search DTO)

#### A search DTO's list is bounded by its `@minsize` / `@maxsize` (#324)
- Why: `postIds: List<Long> @search(in) @ref(postId) @maxsize(200)` on a `@searchable` DTO got no `@Size`, while the
  same mark on a DTO's list did - so one search request could bind any number of values into one `in (...)`, and
  nothing said the mark was dropped.
- Now: a list of a search DTO gets `@Size(min=..., max=..., message=...)` from its `@minsize` / `@maxsize`, as a DTO's
  list does, so a longer list is a 400 before the query is built. Java and Kotlin; a list without the marks, and a list
  an equality turned into one element (`statusItem`), are as before; the 2.x-compatible search DTO is as 2.x wrote it.
  The annotation guide (en, ko, 3.6) says it, and that an empty list - like one not sent - is no condition in JPA,
  MyBatis and MongoDB (every row): a service that means "none" returns an empty page before the search.
- Checked: `SearchSizeTest` (`aSearchedListHoldsWhatItsBoundsAllow`, `kotlinAlike`, `theTwoXOutputIsAsItWas`).
- Files: `SearchDtoBaseRules` (`size`, `listSize`), the annotation guides.
