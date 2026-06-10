# Adding a New Feature

Step-by-step guide for adding a new feature to an OCC extension. Each new feature follows the ServiceLayer pattern: Controller → Facade → Service.

## Steps

1. **Create docs first** — New subdirectory under the extension's `docs/` with `context.md`, `components.md`, `diagram.md`
2. **Define DTOs** in `*-beans.xml` — `*Data` (facade layer) and `*WsDTO` (REST response)
3. **Create Service** — Interface + `Default*` implementation with `@Required` setters
4. **Create Facade** — Interface + `Default*` implementation with Model→Data conversion
5. **Wire Spring beans** in `*-spring.xml` using the alias pattern:
   ```xml
   <alias name="defaultMyService" alias="myService"/>
   <bean id="defaultMyService" class="com.company.ext.services.impl.DefaultMyService">
       <property name="modelService" ref="modelService"/>
   </bean>
   ```
6. **Create Controller** — `@Controller`, `@Secured`, `@Operation`, inject facade via `@Resource`
7. **Build** — `./gradlew yclean ybuild stopServer startServer` (or `./gradlew yclean ybuild stopServer yupdatesystem startServer` if `items.xml` changed — run `yupdatesystem` with the server stopped)
