package xyz.lychee.gatekeeper.shared.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeoRange<T> {
    private final int start;
    private final int end;
    private final T value;

    public boolean contains(int ipNum) {
        return Integer.compareUnsigned(ipNum, this.start) >= 0 &&
                Integer.compareUnsigned(ipNum, this.end) <= 0;
    }
}