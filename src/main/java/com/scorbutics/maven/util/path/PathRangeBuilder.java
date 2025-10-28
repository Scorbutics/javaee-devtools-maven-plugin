package com.scorbutics.maven.util.path;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import lombok.experimental.*;

@UtilityClass
public class PathRangeBuilder {

	public static Stream<Path> range(final Path start, final Path end) {
		if (!end.startsWith(start)) {
			throw new IllegalArgumentException("End path must start with start path");
		}

		final Path relative = start.relativize(end);
		// Generate all intermediate paths
		return IntStream.range(0, relative.getNameCount())
				.mapToObj(i -> {
					if (i == 0) {
						return start;
					}
					return start.resolve(relative.subpath(0, i));
				});
	}
}