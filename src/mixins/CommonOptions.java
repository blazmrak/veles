package mixins;

import picocli.CommandLine.Option;

public class CommonOptions {
	@Option(
		names = { "-v", "--verbose" },
		description = { "Meant to be used when you are having a bad time" }
	)
	public boolean verbose = false;

	@Option(names = { "-N", "--dry-run" }, description = "Do not perform any actions")
	public boolean dryRun = false;
}
