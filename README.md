# Veles

The JDK CLI, reimagined for simplicity

## Features

- `veles run` to skip compilation step
- does not dictate project structure (no verbose `src/main/java`, "resources" are next to the source code)
- autodetects the entrypoint to your app
- `veles compile` - package to `jar`, `uber-jar`, `native` and more, OOTB no config or plugins
- Leyden support with `veles start --train` and `veles start --aot`
- `veles dep` to fuzzy search your local Maven repo for deps instead of having to Google
- `veles format` - includes Eclipse formatter with sane config
- `veles lsp` - generates dotfiles for JdtLS, which also means that your code is formatted as you go
- It has a `--dry-run` option that prints the JDK commands, so that you can learn what is happening under the hood.
- If you hate it or have outgrown it, `pom.xml` is always ready.

## Installation

### Pre-built binaries

> **NOTE:** If you are running ARM Linux or Windows or x86 MacOS, please create an issue.

Download the [latest release](https://github.com/blazmrak/veles/releases/latest)
for your platform and add the binary to `PATH`.
Once you updated the `PATH`, you can test if it worked by running `veles --help`
in a new terminal window.

### Manual

Depending on how you want to run it, there are multiple options:

0. Download the JAR
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
Maven seems like an overkill, you can use Veles.

## Goals

- Have a CLI that is convenient to use
- Be editor friendly
- Make JDK accessible
- Be as transparent as possible about what is going on under the hood
- Handle 90% of cases

## Non-goals

- Be a build tool
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

### Editor

Your editor or IDE is probably complaining that Javalin doesn't exist.

Run `veles lsp`. This will generate the `pom.xml`, but you will probably also
need to refresh the config or restart the language server to see the changes
(`:JdtUpdateConfig` or `:JdtRestart` if you are on LazyVim Neovim distro for
example) or if don't know how to do either, restart the editor. You should now
be in a company of a happy language server once again.

### Project Leyden

If you are using Java 25, you can make a training run by using `veles start --train`
with `--jar` or `--uber-jar`. This will create `.aot` file inside the `target`
directory and you can then use this AOT cache via `veles start --aot` and then
the same `--jar/--uber-jar` flag that you used for training.

### Native

If you try to run the native executable, you will get errors. This is because
Javalin uses reflection, which makes native compilation trickier. Anytime you
get errors when running native executable, start your app using `start --native-reach`
flag, which functions similar to Leyden in that it runs your `classes` or `.jar`
first and uses a java agent to register everything that needs to be present in the
executable during runtime. This cache is present inside `META-INF/native-image`
directory. You should commit this to git, because not all paths might be hit
during a reachability run, so it is important that the new entries are merged
with the existing reachability data.

Now that you have updated the reachability metadata, try compiling to native
again and this time the application should start up normally.

### Formatting

This is my personal gripe with Java, but a lack of a standard formatting tool
and format is a massive PITA for collaboration. For this reason I have implemented
formatting by using Eclipse Formatter, and set sane defaults, so that you don't
have to battle your way through infinite amount of knobs. This format is still
subject to change for settings that are the default now. And it's not always the
way I would like it, but it is at least bearable.

Formatter configuration generated when running `veles lsp` and is located inside
`.settings/format.xml` and is treated as the source of truth. If you use an editor
such as VSCode or Neovim, the settings should be automatically picked up, because
`*.pref` file is also generated, all you need to do is restart JdtLS. For people
using IDEs, you should use the plugin for Eclipse Formatter and point it to the
XML configuration file.

I chose Eclipse by default, because it is integrated into the JdtLS.
If you don't want to use Eclipse, then I have also added the option of using
`Palantir` via `veles format -p` flag. I don't recommend it, because it
lacks integration and because it is worse than mine. You are of course free
to disagree and be wrong.

Also, tabs over spaces, so that we don't have to debate 2 vs 4 and everyone
can set their preference in their editor.

> **Note**: you are free to override any of the settings, Veles won't override
> your changes, but will add anything that you didn't specify

### Resources

Maven and Gradle force you to store resources in a separate
tree such as `src/main/resouces`. I find this mostly annoying, because
it makes it hard to navigate between files, that are actually related. And
it makes simple project structure practically impossible. And the files
end up mashed together in the final artifact anyways. So just put non
`.java` files inside whatever your source directory is, alongside your
source code. I haven't thought of a case, where this isn't a win.

## Why another build tool?

This question constantly pops up in a number of different forms. The answer
is simple: Veles is **not a build tool**, unless you think JDK is a build tool.
The difference is subtle, but important. Build tools like Maven, Gradle, Mill,
etc. etc. don't know how to compile your code and run your tests, the Maven
and Gradle plugins do. The job of these tools is to define the pipeline your
code must go through in order for it to be "built" and you can customize it
pretty much as much as you want. All this to say, build tools concern
themselves with orchestrating, not doing.

Veles does not care about that, it just carries out common operations with a simpler
CLI API. You could use Veles in a Maven or Gradle plugin, but that would not make
much sense, because the plugins for stuff that Veles does already exist... It is
why it is possible to generate the `pom.xml` that behaves the same. It is also why
that `pom.xml` is 100s of lines long.

> Ok buddy, all I hear is a whole bunch of excuses for what is just a shitty
> build tool

I hear you and it might as well be true. But the goal was to be just this,
to raise the bar for what is possible to achieve in a simpler way.
For any project that I do, I will need dependencies, I would like to run tests
and I would like to have a standard code format that doesn't suck and I don't
want to think about any of this, this is the stuff that the JDK should provide
and do so in a nicer way than it currently does. Yes, this project could have
been a template, but I don't want to maintain that kind of configuration,
especially because it is not standard and I'm still left with a bad CLI
experience.

I think this is even more important if you don't deal with Java every day or if
you would want to learn Java. The fact that the new comers have to first learn
the equivalent of Webpack or Groovy/Kotlin, just because they wanted to use a
library, in 2025, or any year for that matter, is... absurd... to me at least.
I haven't had this experience anywhere else, with exception to C.

I built Veles to be a smaller step up if you come from just using the
JDK, than any build tool is. The commands are analogous to what you do when
using the JDK, it's just that the arguments are autofilled for you. It's also
easy to see what is actually happening under the covers. I also purposefully
made it easy to eject either way - back to the JDK or forward to Maven.

### But what about the frameworks?

I will only do what the JDK can. If you want to use Hibernate, Quarkus, Micronaut,
Spring and other frameworks that need plugins for building, you should just use a
proper build tool that is officially supported. I do however think that the
complexity of these frameworks is waved away too often and I do think there is
something nice about the Go's philosophy and seeing all the code that will run
however tedious it might seem at first.

If you want to script, you can lean more heavily into reflection
and can just run without having to compile. If you care about startup/performance,
you can avoid reflection with the [Avaje Project](https://avaje.io/) while still getting similar
developer experience to Jakarta/Spring. If you are coming from other ecosystems,
[Javalin](https://github.com/javalin/javalin) and [Jooby](https://github.com/jooby-project/jooby)
have a similar feel to the frameworks that are used there.
If you have to work with a database, you can replace Hibernate with [jOOQ](https://github.com/jOOQ/jOOQ)
or [Doma](https://github.com/domaframework/doma).

## Commands

The ones with `#[x]` are implemented, the ones with `#[-]` have partial support.
Others are ideas/plans.

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

veles lsp                     # [x]
veles format                  # [x]
veles help                    # [x]
```

## Configuration

```yaml
artifact: group:artifact:version

settings:
  jdk: [<version>-<distro>]
  compiler:
    release: [version]
  native:
    graalVersion: 25
  project:
    src: [src path]
    test: [test path]
  format:
    formatter: eclipse/palantir
    lineWidth: 100
    indent: tab

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
