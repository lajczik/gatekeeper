package xyz.lychee.gatekeeper.shared.objects;

import lombok.Getter;
import lombok.Setter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;

@Setter
@Getter
public abstract class AbstractManager {
    public abstract boolean load(Gatekeeper<?> plugin) throws Exception;

    public abstract boolean unload(Gatekeeper<?> plugin) throws Exception;

    public abstract boolean reload(Gatekeeper<?> plugin) throws Exception;
}
