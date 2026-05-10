package xyz.lychee.gatekeeper.shared.objects;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class PlatformData {
    private final String pluginVersion;
    private final String version;
    private final String name;
    private final boolean onlineMode;
    private volatile int players = 0;
}