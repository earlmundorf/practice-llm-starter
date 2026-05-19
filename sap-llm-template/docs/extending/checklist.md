# New Feature Checklist

- [ ] **Docs** — `context.md`, `components.md`, `diagram.md` in new subdirectory
- [ ] **items.xml** — Types/attributes added? → `ant clean all && ant updatesystem`
- [ ] **beans.xml** — `*Data` and `*WsDTO` DTOs defined → `ant build`
- [ ] **Service** — Interface + `Default*` impl with `@Required` setters
- [ ] **Facade** — Interface + `Default*` impl with Model→Data conversion
- [ ] **Spring XML** — Beans wired with `<alias>` pattern
- [ ] **Controller** — `@Controller`, `@Secured`, `@Operation`, returns `*WsDTO`
- [ ] **Test** — Test class in `testsrc/`
- [ ] **Extension docs** — README updated with new flow
