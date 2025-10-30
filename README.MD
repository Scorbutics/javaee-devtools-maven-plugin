# JavaEE DevTools Maven Plugin

A Maven plugin that brings modern hot-reload capabilities to traditional Java EE / Jakarta EE applications, dramatically improving development workflow by enabling instant deployment of code and resource changes without full application restarts.

## Overview

The JavaEE DevTools Maven Plugin fills a critical gap in the Java EE ecosystem by providing IDE-independent, fast incremental deployment for applications running on traditional application servers (JBoss/WildFly, WebLogic, WebSphere, TomEE, Payara). Unlike existing solutions that require expensive commercial tools or IDE-specific configurations, this plugin integrates directly with Maven's build lifecycle and works consistently across any development environment.

## Why This Plugin?

Traditional Java EE development suffers from slow feedback loops. A typical code change requires:
1. Stop the application server
2. Clean and rebuild
3. Package the application
4. Redeploy the entire EAR/WAR
5. Restart the server
6. Wait 2-5 minutes

This plugin eliminates part of this pain by providing:
- **Fast Compile/Deployment cycle**: Only changed classes need to be compiled and will be instantly pushed to the server without full packaging.
- **Smart Detection**: Automatically detects compilation completion, including post-processing plugins
- **Web Resource Hot Reload**: HTML, CSS, JavaScript changes reflected immediately without restart
- **Zero Configuration**: Sensible defaults work out of the box
- **Team Consistency**: Configuration in pom.xml, version controlled and shared
- **Cross-Platform**: Works on Windows, macOS, and Linux

## When to use this plugin?

### Doing Back-end Development

**Before:**
```
Edit Java code → Wait for IDE → Full rebuild → Redeploy → Wait 2-5 minutes → Test
```

**After:**
```
Edit Java code → Maven compile → Auto-deploy edited files → Redeploy → Wait 1-2 minutes → Test
```

### Doing Web Development

**Before:**
```
Edit HTML/CSS/JS → Full rebuild → Redeploy → Wait 2-5 minutes → Refresh browser → Test
```

**After:**

After starting:

```bash
mvn javaee-devtools:watch-web
```

```
Edit HTML/CSS/JS → Auto-deploy edited files in milliseconds → Refresh browser → Test
```

## Important Note

This plugin is designed for traditional Java EE / Jakarta EE applications deployed on standard application servers using exploded EAR/WAR structures. 
It is not intended for Spring Boot, Quarkus, Micronaut, or other modern frameworks that have their own development tools.

Also, it will never replace HotSwap agents like JRebel or DCEVM+HotswapAgent for deep Java class structure changes (method signatures, class hierarchy).

These tools are still required for maximum productivity and serve a different purpose (debugging, live deep code changes).

However, it significantly speeds up the common case of iterative development where most changes are simple method/body edits or resource updates.

I encourage to use this plugin alongside existing HotSwap solutions to get the best of both worlds.

**If you can't afford commercial tools like JRebel, use the classic JVM HotSwap in debug mode for simple changes, and this plugin for fast deployment of compiled classes and resources.**

## Key Features Goals

### 1. Full archive Deployment

Supports both packaged and exploded EAR/WAR/JAR deployments. Binds to Maven's `install` phase to deploy the full archive after packaging completes:

```xml
<execution>
    <goals>
        <goal>full-deploy</goal>
    </goals>
</execution>
```

- Deploys to exploded EAR/WAR structures on JBoss/WildFly
- Triggers server hot-reload automatically
- Supports multi-module projects (separate WAR and EJB modules)

### 2. Automatic Class Deployment

Binds to Maven's `process-classes` phase to deploy compiled classes immediately after compilation completes:

```xml
<execution>
    <goals>
        <goal>unit-deploy</goal>
    </goals>
</execution>
```

- Deploys to exploded EAR/WAR structures on JBoss/WildFly
- Triggers server hot-reload automatically
- Supports multi-module projects (separate WAR and EJB modules)
- Handles incremental compilation correctly

### 3. Web Resources Watching

Real-time watching and deployment of web resources (HTML, CSS, JavaScript, JSP):

```bash
mvn javaee-devtools:watch-web
```

- Sub-second latency for web resource changes
- Ignore patterns for build artifacts
- Smart event coalescing to avoid spam

On the TODO list:

- Automatic browser refresh (with LiveReload support)
- Configurable file type filters

### 3. All files Watching (experimental)

Real-time watching and deployment of everything:

```bash
mvn javaee-devtools:watch-all
```

- Include all features of `watch-web`
- Clean/installing is still supported because intermediate directories are watched continuously
- Triggers deployment after no event detected for a debounce period
- Detect Maven end of build automatically and trigger copying META-INF data.

On the TODO list:
- Triggers deployment only after Maven build completes

## Architectural Highlights

### 1. Event-Based

Two-phase event processing separates infrastructure concerns from business logic:

- **Technical Events**: Handled synchronously (directory registration, watch state management)
- **Functional Events**: Queued and processed asynchronously (file copying, deployment)
- Clean separation prevents race conditions and ensures reliability

### 2. Smart Progress Indication

User-friendly console output with adaptive display:

- Live progress spinner during processing (when terminal supports it)
- Automatic fallback to milestone logging for redirected output
- UTF-8 symbol auto-detection (with ASCII fallback for legacy Windows consoles)
- Debounced summary to avoid log spam
- Session statistics and metrics

### 3. Reliability Features

Production-ready reliability mechanisms:

- **Overflow Detection**: Handles WatchService buffer overflow gracefully
- **Retry Logic**: Failed deployments automatically retried with exponential backoff
- **Maven Project Auto Detection**: Automatically locates project root and relevant directories reading pom.xml source files and use reconciliation to ensure it matches the deployed structure
- **Configurable Debounce**: Prevents event storms during bulk operations (e.g., Git checkouts, IDE builds)

The plugin uses an event processing architecture:

- **File Watching**: WatchService monitors file system changes
- **Event Classification**: Two-phase processing separates technical and functional concerns
- **Event Coalescing**: Smart debouncing prevents event spam during bulk operations
- **Queue-Based Processing**: Producer-consumer pattern handles high-volume changes

On the TODO list:

- **State Tracking**: Persistent state database to detect changes across restarts
- **Smart Modification Detection**: On unit-deploy goal, only deploy changed projects and files to minimize overhead
 
## Use case Examples

Different deployment configurations to fit custom workflows:

**Manual deployment to target filesystem folder**:

```xml
<configuration>
    <target>${env:JBOSS_HOME}/standalone/deployments</target>
    <!-- Keep two folders synced by copying source to destination -->
    <deployment>
        <source>mymodule/src/main/webapp/dist</source>
        <!-- This deployment target is relative to the main target folder -->
        <target>mymodule.war</target>
    </deployment>
</configuration>
```

**Manual deployment to source filesystem folder**:

```xml
<configuration>
    <!-- Keep two folders synced by copying source to destination -->
    <deployment>
        <!-- This flag makes sure the deployment target is kept relative the source folder and not the target one -->
        <useSourceFilesystemOnly>true</useSourceFilesystemOnly>
        <source>mymodule/src/main/webapp/dist</source>
        <target>mymodule/target/myapp-mymodule/dist</target>
    </deployment>
</configuration>
```

**Classic automatic EAR Deployment**:
```xml
<configuration>
    <target>${env.JBOSS_HOME}/standalone/deployments</target>
</configuration>
```

### Multi-Module Projects

Each module (WAR, EJB) deploys independently, or are bundled into a single EAR depending on your structure.

## Comparison with Alternatives

| Solution | Cost | IDE-Independent | Real-Time | Multi-Module | Configuration |
|----------|------|-----------------|-----------|--------------|---------------|
| **JavaEE DevTools Plugin** | Free | ✅ | ✅ | ✅ | pom.xml |
| JRebel | $500+/year | ✅ | ✅ | ✅ | Complex |
| IDE Features | Free | ❌ | ⚠️ | ⚠️ | IDE-specific |
| WildFly Maven Plugin | Free | ✅ | ❌ | ❌ | pom.xml |
| Spring Boot DevTools | Free | ✅ | ✅ | N/A | Only for Spring |
| Manual Deployment | Free | ✅ | ❌ | ❌ | Scripts |

## Goals

### `full-deploy`

Deploys the full archive as an exploded one.

**Default Phase**: `install`

**Parameters**:
- `target` - Target root server deployment directory (required)

**Example**:
```bash
mvn javaee-devtools:full-deploy
```

### `unit-deploy`

Deploys compiled classes after Maven compilation completes.

**Default Phase**: `process-classes`

**Parameters**:
- `target` - Target root server deployment directory (required)

On the TODO list:

- `onlyIfChanged` - Only deploy if files changed (default: `true`)
- `triggerReload` - Trigger server reload after deployment (default: `true`)

**Example**:
```bash
mvn compile  # Automatically triggers deploy-classes
```

### `watch-web`

Watches and deploys web resources (HTML, CSS, JavaScript, JSP) in real-time.

**Phase**: Not bound to lifecycle (manual execution)

**Parameters**:
- `target` - Target root server deployment directory (required)
- `watcher.verbose` - Show detailed file list (default: `false`)
- `watcher.quiet` - Minimal output (default: `false`)
- `watcher.showProgress` - Show live progress indicator (default: `true`)

On the TODO list:
- `watcher.extensions` - File extensions to watch (default: `html,css,js,jsp,json,xml,png,jpg,gif,svg`)

**Example**:
```bash
mvn javaee-devtools:watch-web
```

### `watch-all`

Watches everything, deploys web resources in real-time, trigger a redeployment on Java class changes.

**Phase**: Not bound to lifecycle (manual execution)

**Parameters**:
- `target` - Target root server deployment directory (required)
- `watcher.verbose` - Show detailed file list (default: `false`)
- `watcher.quiet` - Minimal output (default: `false`)
- `watcher.showProgress` - Show live progress indicator (default: `true`)

**Example**:
```bash
mvn javaee-devtools:watch-all
```

## Configuration Examples

### Basic Configuration

```xml
<configuration>
    <target>${env:JBOSS_HOME}/standalone/deployments</target>
</configuration>
```

### JBoss/WildFly EAR Deployment

```xml
<configuration>
    <target>${env:JBOSS_HOME}/standalone/deployments</target>
    <watcher>
        <showProgress>true</showProgress>
    </watcher>
    <!-- Manually configured internal deployments for the web module, allowing the watchers to be effective -->
    <deployments>
        <deployment>
            <useSourceFilesystemOnly>true</useSourceFilesystemOnly>
            <source>mymodule/src/main/webapp/dist</source>
            <target>mymodule/target/myapp-mymodule/dist</target>
        </deployment>
    </deployments>
</configuration>
```

## Requirements

- **Maven**: 3.6.0 or higher
- **Java**: 8 or higher (plugin runtime)
- **Project**: Java EE 6+ or Jakarta EE 8+ application
- **Server**: Exploded EAR/WAR deployment support (JBoss/WildFly recommended)

## Supported Application Servers

- **JBoss EAP / WildFly**: Full support with automatic reload triggering
- **WebLogic**: Manual reload required
- **WebSphere**: Manual reload required
- **Apache TomEE**: Full support
- **Payara / GlassFish**: Full support
- **Tomcat**: Supported for WAR-only deployments

## Performance

Typical performance metrics:

- **Class Deployment**: 100-500ms to copy, then needs server redeploy time
- **Web Resource Deployment**: 50-200ms (instant in browser)
- **Batch Operations**: Efficiently handles 100+ file changes
- **Memory Footprint**: ~10-20MB additional during watching
- **CPU Usage**: <1% idle, 10-20% during processing

## Contributing

Contributions are welcome! This plugin fills a real gap in the Java EE ecosystem, and there are many opportunities for enhancement:

- Additional application server support
- Remote deployment capabilities
- Integration with CI/CD tools
- Performance optimizations
- Additional file type handlers

## Credits

Built for the Java EE community that still maintains and develops traditional enterprise applications.

---

## Quick Start

1. **Add plugin to your pom.xml**:
```xml
<plugin>
    <groupId>com.scorbutics.maven</groupId>
    <artifactId>javaee-devtools-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>unit-deploy</goal>
            </goals>
        </execution>
        <execution>
            <goals>
                <goal>full-deploy</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <target>/your/path/to/your/deployments/folder</target>
    </configuration>
</plugin>
```

2. **Deploy your (already packaged) application** as exploded EAR to application server

```bash
mvn javaee-devtools:full-deploy
```

3. **Start developing**:
```bash
# Terminal 1: Continuous compilation
mvn compile -DcompileIncrementally=true

# Terminal 2: Watch web resources (optional)
mvn javaee-devtools:watch-web
```

4. **Edit code** and see fast changes!

---

**Transform your Java EE development experience from frustrating to fluid. Try JavaEE DevTools Maven Plugin today!**

---