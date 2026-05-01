package xyz.lychee.gatekeeper.shared.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StoredPlayer {
    private String name;
    private int address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredPlayer)) return false;
        StoredPlayer other = (StoredPlayer) o;
        return this.address == other.address
                && Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.address);
    }
}
