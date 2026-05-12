package xyz.lychee.gatekeeper.shared.objects;

import com.grack.nanojson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.function.Consumer;

@Getter
@Setter
@RequiredArgsConstructor
public class PlatformData {
    private final String pluginVersion;
    private final int serviceId;
    private final String platform;
    private final Consumer<JsonObject> consumer;
}