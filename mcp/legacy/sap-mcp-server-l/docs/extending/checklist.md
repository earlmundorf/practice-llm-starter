# New Feature Checklist

- [ ] **Docs** — `context.md`, `components.md`, `diagram.md` in new subdirectory
- [ ] **items.xml** — Types/attributes added? → `./gradlew yclean ybuild stopServer yupdatesystem startServer`
- [ ] **beans.xml** — `*Data` and `*WsDTO` DTOs defined → `./gradlew yclean ybuild`
- [ ] **Service** — Interface + `Default*` impl with `@Required` setters
- [ ] **Facade** — Interface + `Default*` impl with Model→Data conversion
- [ ] **Spring XML** — Beans wired with `<alias>` pattern
- [ ] **Controller** — `@Controller`, `@Secured`, `@Operation`, returns `*WsDTO`
- [ ] **Test** — Test class in `testsrc/`
- [ ] **Extension docs** — README updated with new flow
