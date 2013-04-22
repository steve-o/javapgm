/* 
 */

//import static Preconditions.checkArgument;
//import static Preconditions.checkNotNull;
//import static UnsignedInts.INT_MASK;
//import static UnsignedInts.compare;
//import static UnsignedInts.toLong;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public final class UnsignedInteger extends Number implements Comparable<SequenceNumber> {
	public static final UnsignedInteger ZERO = fromIntBits (0);
	public static final UnsignedInteger ONE = fromIntBits (1);
	public static final UnsignedInteger MAX_VALUE = fromIntBits (-1);

	private final int value;

	private UnsignedInteger (int value) {
		this.value = value & 0xffffffff;
	}

	public static UnsignedInteger fromIntBits (int bits) {
		return new UnsignedInteger (bits);
	}

	public static UnsignedInteger valueOf (long value) {
		checkArgument ((value & INT_MASK) == value,
			"value (%s) is outside the range for an unsigned integer value", value);
		return fromIntBits ((int)value);
	}

	public static UnsignedInteger valueOf (String string) {
		return valueOf (string, 10);
	}

	public static UnsignedInteger valueOf (String string, int radix) {
		return fromIntBits (UnsignedInts.parseUnsignedInt (string, radix));
	}

	@Override
	public int intValue() {	/* Number interface */
		return this.value;
	}

	@Override
	public long longValue() {
		return toLong (this.value);
	}

	@Override
	public float floatValue() {	/* Number interface */
		return longValue();
	}

	@Override
	public double doubleValue() {	/* Number interface */
		return longValue();
	}

	@Override
	public int compareTo (UnsignedInteger other) {
		checkNotNull (other);
		return compare (this.value, other.value);
	}

	@Override
	public int hashCode() {
		return this.value;
	}

	@Override
	public boolean equals (@Nullable Object obj) {
		if (obj instanceof UnsignedInteger) {
			UnsignedInteger other = (UnsignedInteger)obj;
			return this.value == other.value;
		}
		return false;
	}

	@Override
	public String toString() {
		return toString (10);
	}

	public String toString (int radix) {
		return UnsignedInts.toString (value, radix);
	}
}

/* eof */
