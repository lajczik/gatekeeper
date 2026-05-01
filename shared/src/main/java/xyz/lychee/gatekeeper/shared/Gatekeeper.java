package xyz.lychee.gatekeeper.shared;

import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.util.ColoredLogger;

import java.io.File;
import java.io.InputStream;

public interface Gatekeeper<T> {
    InputStream resource(String name);

    File dataFolder();

    ColoredLogger logger();

    String version();

    AbstractLang<T> language();

    CommandPlayer<T> commandPlayer(Object player);
}
