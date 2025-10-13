# Veles

Java CLI for humans.

## What to expect?

From the root of your project, you can just run `veles run` or if
you want to compile, then run `veles compile --uber` - This is how Veles is compiled.

## Installation

### Manual

Depending on how you want to run it, there are multiple options:

1. Install SDKMAN (Optional)
2. Install JDK 24+
3. Install GraalVM (Optional)
4. Compile to native:
   - Compile with `native-image -jar <veles-jar> -o veles`
   - Try to run `./veles --help`
   - Move the executable to a global path (e.g. `/usr/bin/veles`) (Optional)

## Motivation

I have a dislike for build tools but am always stuck with them because of
dependencies. I came across the talk about using Java for simple scripts,
and got curious how far could you come with modern JDK realistically. The
answer is "not that far". But by just having a some opinions and not solving
for every possible problem under the sun, the answer is "surprisingly far".
So if you are just developing an application or writing a script and
Maven seems like an overkill, you can use Veles. If at any point you
feel like Maven would be better, you can export Veles project to Maven
via `veles export`.

## Goals

- Have a CLI that is convenient to use
- Be editor friendly
- Make JDK accessible
- Be as transparent as possible about what is going on under the hood
- Handle 90% of cases

## Non-goals

- Be a full blown build tool
- Support traditional IDEs
- Support Windows, at least for now
- Support JDK <24
- Support any other JVM languages

## Getting started

Create a `HelloWorld.java`

```java
void main() {
  IO.println("Hello, world!");
}
```

Now that we have the minimal example ready, we can test it by running `veles run`.
We should see `Hello, world!` in the console. If you are curious what actually happened
you can run `veles run --dry-run` or `veles run -N`, which will print all the JDK CLI
commands that get executed.

```bash
java ./HelloWorld.java
```

So how does that work? Veles looks for any file that is named `App.java` in the current
directory tree as the entrypoint for the program. If it fails to find that file, it
looks for any `.java` file, that has a main method.

Let's test this, by moving the file into `src` directory. Now we can see that Veles
executes `java ./src/HelloWorld.java`.

### Compiling

Let's compile it now. Run `veles compile` and you will see `target` folder. Veles
(re)uses Maven conventions for output, because there was no need for yet another
folder structure. Let's check out what actually happened:

```bash
java --source-path ./src -d target/classes ./src/HelloWorld.java
```

Here we can see that it not only found the entrypoint, but also calculated the base
directory of the project using the `package`.

Compile has a bunch of packaging options that you can use to package your application.
Run `veles compile -jun` and check out the `target` folder. You will see a **J**ar,
an **U**ber-jar and a **N**ative executable.

You can run each one by using a respective `veles start` command -
e.g. `veles start --uber`.

### Dependencies

This is probably the reason you are considering even using Veles.
Let's add Javalin to our project.

Veles comes with a handy way to add dependencies. Just run `veles dep` and you
will be prompted to enter a dependency. Veles will do a fuzzy search your
dependencies inside your local Maven repository (`$HOME/.m2/repository/`).
If you don't find what you are looking for, you can press `CTRL-u` and
a query will be made to Maven central. The API is rate limited, so there
is a chance that your request will just time out.

If all else fails, you can just add it manually by creating `veles.yaml`:

```yaml
dependencies:
  - "io.javalin:javalin:<version>"
```

Now let's rename our `HelloWorld.java` into `Main.java` and update it with
the following code:

```java
import io.javalin.Javalin;

void main() {
  Javalin.create()
            .get("/", ctx -> ctx.result("Hello World\n"))
            .start(8080);
}
```

To run we can still just use `veles run`. Now run `curl http://localhost:8080` and
pat yourself on the back for having an HTTP server up and running without the
build tool ceremony.

### Project Leyden

If you are using Java 25, you can make a training run by using `veles start --train`
with `--jar` or `--uber-jar`. This will create `.aot` file inside the `target`
directory and you can then use this AOT cache via `veles start --aot` and then
the same `--jar/--uber-jar` flag that you used for training.

### Native

If you try to run the native executable, you will get errors. This is because
Javalin uses reflection, which makes native compilation trickier. Anytime you
get errors when running native executable, compile using `--reach` flag, which
functions similar to Leyden in that it runs your `classes` or `app-uber.jar`
first and uses a java agent to register everything that needs to be present in the
executable during runtime. This cache is present inside `META-INF/native-image`
directory. You should commit this to git, because not all paths might be hit
during a reachability run, so it is important that the new entries are merged
with the existing reachability data.

### Editor

If you are using VS Code or Neovim, you are probably using the Eclipse language
server (LS). And it is complaining because it is certain that Javalin does not exist.

Run `veles lsp`. This will generate the necessary files for JdtLS, but you will
need to refresh the config or restart the LS (`:JdtUpdateConfig` or `:JdtRestart`
if you are on LazyVim Neovim distro for example) or if don't know how to do either,
restart the editor. You should now be in a company of a happy and useful LS.

### Formatting

This is my personal gripe with Java, but a lack of a standard formatting tool
and format is a massive PITA for collaboration. For this reason I have implemented
formatting by using Eclipse Formatter, and set sane defaults, so that you don't
have to battle your way through infinite amount of knobs. This format is still
subject to change for settings that are the default now. And it's not always the
way I would like it, but it is at least bearable. JdtLS should pick up the
settings automatically and should format the files accordingly. Veles reuses the
settings from `.settings/*.pref`.

I chose Eclipse by default, because it is integrated into the JdtLS.
If you don't want to use Eclipse, then I have also added the option of using
`Palantir` via `veles format -p` flag. I don't recommend it, because it
lacks integration and because it is worse than mine. You are of course free
to disagree and be wrong.

Also, tabs over spaces, so that we don't have to debate 2 vs 4 and everyone
can set their preference in their editor.

### Resources

Maven and Gradle force you to store resources in a separate
tree such as `src/main/resouces`. I find this mostly annoying, because
it makes it hard to navigate between files, that are actually related. And
it makes simple project structure practically impossible. And the files
end up mashed together in the final artifact anyways. So just put non
`.java` files inside whatever your source directory is, alongside your
source code. I haven't thought of a case, where this isn't a win.

## Commands

The ones with `#[x]` are implemented, the ones with `#[-]` have partial support. Others are
ideas/plans.

```sh
veles run --watch             # [x]

veles compile                 # [x]
veles compile --jar           # [x]
veles compile --uber          # [x]
veles compile --native        # [x]
veles compile --native-reach  # [x]
veles compile --zip           # [x]
veles compile --docker        # [x]
veles compile --exploded      # [x]

veles start                   # [x]
veles start --jar             # [x]
veles start --native          # [x]
veles start --aot             # [x]
veles start --train           # [x]

veles init                    # [-]
veles config                  # [-]

veles dep                     # [x]
veles dep update --major
veles dep update --minor
veles dep update --patch

veles dev --watch             # [-]
veles test --watch

veles install
veles publish

veles export --maven          # [x]

veles lsp                     # [x]
veles format                  # [x]
veles help                    # [x]
```

## Configuration

```yaml
artifact: group:artifact:version

settings:
  compiler:
    release: 25

dependencies:
    - group:artifact:version
    - -group:provided:version
    - +group:runtime:version
    - '#group:pom:version'
    - '!group:test:version'
    - @group:annotation-processor:version
```

### Scopes

I have made a potentially regrettable decision, that I'll just have
scopes as prefixes. Hopefully, everyone finds them intuitive enough.
If not, use `veles dep`.

- `no prefix` - compile (default)
- `-` - provided (not present in the final artifact)
- `+` - runtime (added into the final artifact)
- `#` - pom (specifies version numbers)
- `!` - test (!important)
- `@` - annotation processor (not a scope, but gets added to processor path at compile time)

## Acknowledgements

- Huge shoutout to Cay Horstmann for the talk that inspired this project [Java for Small Coding Tasks](https://www.youtube.com/watch?v=04wFgshWMdA)
- [JPM](https://github.com/codejive/java-jpm) where I discovered MIMA and JLine.

## Similar projects

- [JBang](https://github.com/jbangdev/jbang)
- [JPM](https://github.com/codejive/java-jpm)
- [JResolve](https://github.com/bowbahdoe/jresolve-cli)
- [Pottery](https://github.com/kmruiz/pottery/)
