package common;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() throws Exception {
		try (var in = getClass().getResourceAsStream("/VERSION")) {
			var version = new String(in.readAllBytes());
			version = version.substring(0, version.length() - 1);

			// TODO: Improve format here
			if (System.console() != null) {
				return new String[] { "Veles " + version, "Os: %s".formatted(Os.name()),
					"Version: %s".formatted(Os.version()), "Arch: %s".formatted(Os.arch()) };
			} else {
				return new String[] { version };
			}
		}
	}
}
