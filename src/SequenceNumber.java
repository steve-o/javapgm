/* Wrapper for Java primitives data type to handle window semantics of
 * comparison and wrapping.
 */
public final class SequenceNumber extends Number implements Comparable<SequenceNumber> {

	public static final long   MIN_VALUE = 0L;
	public static final long   MAX_VALUE = 0xffffffffL;

	private long value;

	public SequenceNumber (long value) {
		this.assign (value);
	}

	public void assign (long value) {
		this.value = value;
	}

	public void assign (SequenceNumber sequence) {
		this.assign (sequence.longValue());
	}

	public int intValue() {	/* Number interface */
		return -1;
	}

	public long longValue() {
		return value;
	}

	public float floatValue() {	/* Number interface */
		return (float)-1.0;
	}

	public double doubleValue() {	/* Number interface */
		return (double)-1.0;
	}

	public String toString() {
		return String.valueOf (value);
	}

	public int hashCode() {
		return (int)value;
	}

	public boolean equals (Object obj) {
		if (obj instanceof SequenceNumber) {
			return value == ((SequenceNumber)obj).longValue();
		}
		return false;
	}

	public boolean lt (SequenceNumber anotherSqn) {
		long thisVal = this.value;
		long anotherVal = anotherSqn.value;
		return (thisVal - anotherVal) < 0;
	}

	public boolean lte (SequenceNumber anotherSqn) {
		long thisVal = this.value;
		long anotherVal = anotherSqn.value;
		return (thisVal == anotherVal) || ((thisVal - anotherVal) < 0);
	}

	public boolean gt (SequenceNumber anotherSqn) {
		long thisVal = this.value;
		long anotherVal = anotherSqn.value;
		return (anotherVal - thisVal) < 0;
	}

	public boolean gte (SequenceNumber anotherSqn) {
		long thisVal = this.value;
		long anotherVal = anotherSqn.value;
		return (thisVal == anotherVal) || ((anotherVal - thisVal) < 0);
	}

	public int compareTo (SequenceNumber anotherSqn) {
		return this.lt (anotherSqn) ? -1 : (this.equals (anotherSqn) ? 0 : 1);
	}

	public SequenceNumber increment() {
		if (this.MAX_VALUE == this.value) {
			this.value = this.MIN_VALUE;
		} else {
			this.value++;
		}
		return this;
	}

	public long nextValue() {
		if (this.MAX_VALUE == this.value) {
			return this.MIN_VALUE;
		} else {
			return this.value + 1;
		}
	}

	public static final int SIZE = 32;
}

/* eof */
