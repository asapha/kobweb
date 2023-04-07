import com.varabyte.kobweb.cli.common.DEFAULT_BRANCH
import com.varabyte.kobweb.cli.common.DEFAULT_REPO
import com.varabyte.kobweb.cli.conf.handleConf
import com.varabyte.kobweb.cli.create.handleCreate
import com.varabyte.kobweb.cli.export.handleExport
import com.varabyte.kobweb.cli.list.handleList
import com.varabyte.kobweb.cli.run.handleRun
import com.varabyte.kobweb.cli.stop.handleStop
import com.varabyte.kobweb.cli.version.handleVersion
import com.varabyte.kobweb.server.api.ServerEnvironment
import com.varabyte.kobweb.server.api.SiteLayout
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.optional

private enum class Mode {
    /** Expect a user at an ANSI-enabled terminal interacting with the command */
    INTERACTIVE,

    /** Expect the command to run in a constrained environment, e.g. a server, without user interaction */
    DUMB
}

private fun ArgParser.mode() = option(
    ArgType.Choice<Mode>(),
    fullName = "mode",
    shortName = "m",
    description = "(DEPRECATED, use `--[no]tty` instead) If interactive, runs in an ANSI-enabled terminal expecting user input. If dumb, command only outputs, using simple console logging",
)

private fun ArgParser.layout() = option(
    ArgType.Choice<SiteLayout>(),
    fullName = "layout",
    shortName = "l",
    description = "Specify the organizational layout of the site files.",
).default(SiteLayout.KOBWEB)

private fun ArgParser.tty() = option(
    ArgType.Boolean,
    fullName = "tty",
    description = "Enable TTY support (default behavior). Tries runs in an ANSI-enabled terminal in an interactive mode if it can. Otherwise, falls back to `--notty` mode.",
)

private fun ArgParser.notty() = option(
    ArgType.Boolean,
    fullName = "notty",
    description = "Explicitly disable TTY support. In this case, runs in plain mode, logging output sequentially without user input, which is useful for CI environments or Docker containers.",
)

private const val VERSION_HELP = "Print the version of this binary"

private fun Mode.toTtyParam() = when (this) {
    Mode.INTERACTIVE -> "--tty"
    Mode.DUMB -> "--notty"
}

private fun Mode.printDeprecationWarning() {
    println("WARNING: `--mode ${this.name.lowercase()}` is deprecated and will be removed in a future version. Please use `${this.toTtyParam()}` instead.")
}

/**
 * Resolve the current way to determine if we should use ANSI support.
 *
 * We currently have two approaches, the current way and the legacy way, to determine if we should try running with ANSI
 * support. This helper function resolves them, preferring the current way if present, or falling back to the legacy way
 * with a warning otherwise. If neither is present, we default to true.
 */
private fun shouldUseAnsi(tty: Boolean?, notty: Boolean?, mode: Mode?): Boolean {
    if (tty != null && notty != null) {
        println("WARNING: Both `--tty` and `--notty` are specified. Ignoring `--notty`.")
    }
    if ((tty != null || notty != null) && mode != null) {
        println("WARNING: Both `--mode` and `--[no]tty` are specified. Ignoring `--mode`.")
    }
    return tty
        ?: notty?.not()
        ?: @Suppress("NAME_SHADOWING") mode?.let { mode ->
            mode.printDeprecationWarning()
            mode == Mode.INTERACTIVE
        }
        ?: true
}

@ExperimentalCli
fun main(args: Array<String>) {
    val parser = ArgParser("kobweb")

    class Version : Subcommand("version", VERSION_HELP) {
        override fun execute() {
            handleVersion()
        }
    }

    class List : Subcommand("list", "List all project templates") {
        val repo by option(ArgType.String, "repo", "The repository that hosts Kobweb templates")
            .default(DEFAULT_REPO)
        val branch by option(ArgType.String, "branch", "The branch in the repository to use")
            .default(DEFAULT_BRANCH)

        override fun execute() {
            handleList(repo, branch)
        }
    }

    class Create : Subcommand("create", "Create a Kobweb app / site") {
        val template by argument(ArgType.String, "template", "The name of the template to instantiate, e.g. 'site'. If not specified, choices will be presented.").optional()
        val repo by option(ArgType.String, "repo", "The repository that hosts Kobweb templates")
            .default(DEFAULT_REPO)
        val branch by option(ArgType.String, "branch", "The branch in the repository to use")
            .default(DEFAULT_BRANCH)

        override fun execute() {
            handleCreate(repo, branch, template)
        }
    }

    class Export : Subcommand("export", "Generate a static version of a Kobweb app / site") {
        val tty by tty()
        val notty by notty()
        val mode by mode()
        val layout by layout()

        override fun execute() {
            handleExport(layout, shouldUseAnsi(tty, notty, mode))
        }
    }

    class Run : Subcommand("run", "Run a Kobweb server") {
        val env by option(ArgType.Choice<ServerEnvironment>(), "env").default(ServerEnvironment.DEV)
        val tty by tty()
        val notty by notty()
        val mode by mode()
        val layout by layout()

        override fun execute() {
            handleRun(env, layout, shouldUseAnsi(tty, notty, mode))
        }
    }

    class Stop : Subcommand("stop", "Stop a Kobweb server if one is running") {
        val tty by tty()
        val notty by notty()
        val mode by mode()

        override fun execute() {
            handleStop(shouldUseAnsi(tty, notty, mode))
        }
    }

    class Conf : Subcommand("conf", "Query a value from the .kobweb/conf.yaml file (e.g. \"server.port\")") {
        val query by argument(ArgType.String, "query", "The query to search the .kobweb/conf.yaml for (e.g. \"server.port\")")
        override fun execute() {
            handleConf(query)
        }
    }

    // I'm not too happy with the redundancy here, because we already have the Version subcommand which makes more
    // sense. However, by convention users expect "-v" and "--version" to also work. `git` itself supports both
    // approaches, so if Linus can accept such a compromise, so can I...
    val version by parser.option(ArgType.Boolean, "version", "v", VERSION_HELP)

    parser.subcommands(Version(), List(), Create(), Export(), Run(), Stop(), Conf())
    parser.parse(args.takeIf { it.isNotEmpty() } ?: arrayOf("-h"))

    // It doesn't make sense to show the version if the option is combined with any other command or option
    if (args.size == 1 && version == true) {
        handleVersion()
    }
}