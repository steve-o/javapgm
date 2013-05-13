/* Wrapper for Java primitives data type to handle window semantics of
 * comparison and wrapping.
 */

//import static Preconditions.checkArgument;
//import static Preconditions.checkNotNull;
//import static UnsignedInts.INT_MASK;
//import static UnsignedInts.compare;
//import static UnsignedInts.toLong;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

@SuppressWarnings("serial")
public final class SequenceNumber extends Number implements Comparable<SequenceNumber> {
	public static final SequenceNumber ZERO = fromIntBits (0);
	public static final SequenceNumber ONE = fromIntBits (1);
	public static final SequenceNumber MAX_VALUE = fromIntBits (-1);

	private final int value;

	private SequenceNumber (int value) {
		this.value = value & 0xffffffff;
	}

	public static SequenceNumber fromIntBits (int bits) {
		return new SequenceNumber (bits);
	}

	public static SequenceNumber valueOf (long value) {
		Preconditions.checkArgument ((value & UnsignedInts.INT_MASK) == value,
			"value (%s) is outside the range for an unsigned integer value", value);
		return fromIntBits ((int)value);
	}

	public static SequenceNumber valueOf (String string) {
		return valueOf (string, 10);
	}

	public static SequenceNumber valueOf (String string, int radix) {
		return fromIntBits (UnsignedInts.parseUnsignedInt (string, radix));
	}

	@CheckReturnValue
	public SequenceNumber plus (int val) {
		return fromIntBits (this.value + val);
	}

	@CheckReturnValue
	public SequenceNumber plus (SequenceNumber val) {
		return fromIntBits (this.value + Preconditions.checkNotNull (val).value);
	}

	@CheckReturnValue
	public SequenceNumber minus (int val) {
		return fromIntBits (this.value - val);
	}

	@CheckReturnValue
	public SequenceNumber minus (SequenceNumber val) {
		return fromIntBits (this.value - Preconditions.checkNotNull (val).value);
	}

	@Override
	public int intValue() {	/* Number interface */
		return this.value;
	}

	@Override
	public long longValue() {
		return UnsignedInts.toLong (this.value);
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
	public int compareTo (SequenceNumber other) {
		Preconditions.checkNotNull (other);
		return UnsignedInts.compare (this.value, other.value);
	}

	@Override
	public int hashCode() {
		return this.value;
	}

	@Override
	public boolean equals (@Nullable Object obj) {
		if (obj instanceof SequenceNumber) {
			SequenceNumber other = (SequenceNumber)obj;
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

	public boolean lt (SequenceNumber other) {
		Preconditions.checkNotNull (other);
		return (this.value - other.value) < 0;
	}

	public boolean lte (SequenceNumber other) {
		Preconditions.checkNotNull (other);
		return (this.value == other.value) || ((this.value - other.value) < 0);
	}

	public boolean gt (SequenceNumber other) {
		Preconditions.checkNotNull (other);
		return (other.value - this.value) < 0;
	}

	public boolean gte (SequenceNumber other) {
		Preconditions.checkNotNull (other);
		return (this.value == other.value) || ((other.value - this.value) < 0);
	}

	public static final int SIZE = 32;
}

/* eof */