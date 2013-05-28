/*
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.util.Collection;

public final class Ints {
	private Ints() {}

	public static int compare(int a, int b) {
		return (a < b) ? -1 : ((a > b) ? 1 : 0);
	}
        
        public static int[] toArray(Collection<? extends Number> collection) {
                Object[] boxedArray = collection.toArray();
                int len = boxedArray.length;
                int[] array = new int[len];
                for (int i = 0; i < len; i++) {
                        // checkNotNull for GWT (do not optimize)
                        array[i] = ((Number) checkNotNull(boxedArray[i])).intValue();
                }
                return array;
        }
        
        
}

/* eof */