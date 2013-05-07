/*
 */

import javax.annotation.Nullable;

public final class Preconditions {
	private Preconditions() {}

	public static void checkArgument (boolean expression) {
		if (!expression) {
			throw new IllegalArgumentException();
		}
	}

	public static void checkArgument (boolean expression, @Nullable Object errorMessage) {
		if (!expression) {
			throw new IllegalArgumentException (String.valueOf (errorMessage));
		}
	}

	public static void checkArgument (boolean expression, @Nullable String errorMessageTemplate, @Nullable Object... errorMessageArgs) {
		if (!expression) {
			throw new IllegalArgumentException (format (errorMessageTemplate, errorMessageArgs));
		}
	}

	public static <T> T checkNotNull (T reference) {
		if (reference == null) {
			throw new NullPointerException();
		}
		return reference;
	}

	static String format (String template, @Nullable Object... args) {
		template = String.valueOf (template); // null -> "null"

// start substituting the arguments into the '%s' placeholders
		StringBuilder builder = new StringBuilder (template.length() + 16 * args.length);
		int templateStart = 0;
		int i = 0;
		while (i < args.length) {
			int placeholderStart = template.indexOf ("%s", templateStart);
			if (placeholderStart == -1) {
				break;
			}
			builder.append (template.substring (templateStart, placeholderStart));
			builder.append (args[i++]);
			templateStart = placeholderStart + 2;
		}
		builder.append (template.substring (templateStart));

// if we run out of placeholders, append the extra args in square braces
		if (i < args.length) {
			builder.append (" [");
			builder.append (args[i++]);
			while (i < args.length) {
				builder.append (", ");
				builder.append (args[i++]);
			}
			builder.append (']');
		}

		return builder.toString();
	}
}

/* eof */
