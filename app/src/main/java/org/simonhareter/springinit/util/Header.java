package org.simonhareter.springinit.util;

import java.util.List;

public record Header(
        List<TextSegment> segments) implements DialogRow {
}
