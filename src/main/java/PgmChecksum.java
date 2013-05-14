/* Checksum computation for PGM packets using one's complement arithmetic.
 */
@SuppressWarnings("PointlessBitwiseExpression")
public class PgmChecksum {

	public static long partial (byte[] buf, int offset, int length, long sum)
	{
		int i = 0;
		while (length > 1) {
			int w = ((buf[ offset + i + 0 ] & 0xff) << 8) +
				((buf[ offset + i + 1 ] & 0xff) << 0);
			sum += w;
			if ((sum & 0x80000000) > 0)
				sum = (sum & 0xffff) + (sum >> 16);
			length -= 2;
			i += 2;
		}
		if (length > 0)
			sum += buf[ offset + i ];
		return sum;
	}

	public static long partialCopy (byte[] src, int srcOffset, byte[] dst, int dstOffset, int length, long sum)
	{
		System.arraycopy (src, srcOffset, dst, dstOffset, length);
		return PgmChecksum.partial (dst, dstOffset, length, sum);
	}

	public static int fold (long sum)
	{
		while ((sum >> 16) > 0)
			sum = (sum & 0xffff) + (sum >> 16);
		return sum == 0xffff ? (int)sum : (int)~sum;
	}

	public static long blockAdd (long sum, long sum2, int offset)
	{
		if ((offset & 1) > 0)
			sum2 = ((sum2 & 0xff00ff) << 8) + ((sum2 >> 8) & 0xff00ff);
		sum += sum2;
		return sum + ((sum < sum2) ? 1 : 0);
	}
}

/* eof */
