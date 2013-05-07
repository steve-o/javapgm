/*
 */

public final class UnsignedInts {
	static final long INT_MASK = 0xffffffffL;

	private UnsignedInts() {}

	static int flip (int value) {
		return value ^ Integer.MIN_VALUE;
	}

	public static int compare (int a, int b) {
		return Ints.compare (flip(a), flip(b));
	}

	public static long toLong (int value) {
		return value & INT_MASK;
	}

	public static int parseUnsignedInt (String s) {
		return parseUnsignedInt (s, 10);
	}

	public static int parseUnsignedInt (String string, int radix) {
		Preconditions.checkNotNull (string);
		long result = Long.parseLong (string, radix);
		if ((result & INT_MASK) != result) {
			throw new NumberFormatException ("Input " + string + " in base " + radix
			+ " is not in the range of an unsigned integer");
		}
		return (int)result;
	}

	public static String toString (int x) {
		return toString (x, 10);
	}

	public static String toString (int x, int radix) {
		long asLong = x & INT_MASK;
		return Long.toString (asLong, radix);
	}
}

/* eof */
