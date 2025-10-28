package com.scorbutics.maven.model.packaging.computed;

import java.util.*;

public interface ModuleTreePrinter {
	StringBuilder printStructure(final List<ComputedModule> structure);
}