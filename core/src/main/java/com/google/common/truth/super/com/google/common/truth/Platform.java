/*
 * Copyright (c) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.truth;

import static com.google.common.truth.Platform.ComparisonFailureMessageStrategy.INCLUDE_COMPARISON_FAILURE_GENERATED_MESSAGE;
import static com.google.common.truth.StringUtil.format;
import static com.google.common.truth.Truth.appendSuffixIfNotNull;
import static java.lang.Double.parseDouble;
import static java.lang.Float.parseFloat;
import static jsinterop.annotations.JsPackage.GLOBAL;

import com.google.common.collect.ImmutableList;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Extracted routines that need to be swapped in for GWT, to allow for minimal deltas between the
 * GWT and non-GWT version.
 *
 * @author Christian Gruber (cgruber@google.com)
 */
final class Platform {
  private Platform() {}

  /** Returns true if the instance is assignable to the type Clazz. */
  static boolean isInstanceOfType(Object instance, Class<?> clazz) {
    if (clazz.isInterface()) {
      throw new UnsupportedOperationException(
          "Under GWT, we can't determine whether an object is an instance of an interface Class");
    }

    for (Class<?> current = instance.getClass();
        current != null;
        current = current.getSuperclass()) {
      if (current.equals(clazz)) {
        return true;
      }
    }
    return false;
  }

  enum ComparisonFailureMessageStrategy {
    OMIT_COMPARISON_FAILURE_GENERATED_MESSAGE,
    INCLUDE_COMPARISON_FAILURE_GENERATED_MESSAGE;
  }

  abstract static class PlatformComparisonFailure extends AssertionError {
    PlatformComparisonFailure(
        String message,
        String expected,
        String actual,
        String suffix,
        Throwable cause,
        ComparisonFailureMessageStrategy messageStrategy) {
      // Give super() the full message because j2cl would ignore a getMessage() override: b/62038327
      super(makeMessage(message, expected, actual, suffix, messageStrategy), cause);
    }

    private static String makeMessage(
        String message,
        String expected,
        String actual,
        String suffix,
        ComparisonFailureMessageStrategy messageStrategy) {
      String body =
          messageStrategy == INCLUDE_COMPARISON_FAILURE_GENERATED_MESSAGE
              ? format("%s expected:<[%s]> but was:<[%s]>", message, expected, actual)
              : message;
      return appendSuffixIfNotNull(body, suffix);
    }

    @Override
    public final String toString() {
      return getLocalizedMessage();
    }
  }

  /** Determines if the given subject contains a match for the given regex. */
  static boolean containsMatch(String subject, String regex) {
    return compile(regex).test(subject);
  }

  /**
   * Returns an array containing all of the exceptions that were suppressed to deliver the given
   * exception. Delegates to the getSuppressed() method on Throwable that is available in Java 1.7+
   */
  static Throwable[] getSuppressed(Throwable throwable) {
    return throwable.getSuppressed();
  }

  static void cleanStackTrace(Throwable throwable) {
    // Do nothing. See notes in StackTraceCleanerTest.
  }

  @NullableDecl
  static ImmutableList<Fact> makeDiff(String expected, String actual) {
    /*
     * IIUC, GWT messages lose their newlines by the time users see them. Given that, users are
     * likely better served by showing the expected and actual values with mangled newlines than by
     * showing a diff with mangled newlines (which would look similar but with + and - inserted into
     * it). Hopefully no one under GWT has long, nearly identical messages. In any case, they've
     * always been stuck like this.
     */
    return null;
  }

  static String doubleToString(double value) {
    // This probably doesn't match Java perfectly, but we do our best.
    if (value == Double.POSITIVE_INFINITY) {
      return "Infinity";
    } else if (value == Double.NEGATIVE_INFINITY) {
      return "-Infinity";
    } else if (value == 0 && 1 / value < 0) {
      return "-0.0";
    } else {
      // TODO(cpovirk): Would it make more sense to pass `undefined` for the locale? But how?
      // Then again, we're already hardcoding "Infinity," an English word, above....
      String result = ((Number) (Object) value).toLocaleString("en-US", JavaLikeOptions.INSTANCE);
      return (parseDouble(result) == value) ? result : Double.toString(value);
    }
  }

  static String floatToString(float value) {
    // This probably doesn't match Java perfectly, but we do our best.
    if (value == Float.POSITIVE_INFINITY) {
      return "Infinity";
    } else if (value == Float.NEGATIVE_INFINITY) {
      return "-Infinity";
    } else if (value == 0 && 1 / value < 0) {
      return "-0.0";
    } else if (value == 0) {
      return "0.0";
    } else {
      // TODO(cpovirk): Would it make more sense to pass `undefined` for the locale? But how?
      // Then again, we're already hardcoding "Infinity," an English word, above....
      String result = ((Number) (Object) value).toLocaleString("en-US", JavaLikeOptions.INSTANCE);
      return (parseFloat(result) == value) ? result : Float.toString(value);
    }
  }

  /** Tests if current platform is Android which is always false. */
  static boolean isAndroid() {
    return false;
  }

  /** Returns a human readable string representation of the throwable's stack trace. */
  static String getStackTraceAsString(Throwable throwable) {
    // TODO(cpovirk): Write a naive implementation that at least dumps the main exception's stack.
    return throwable.toString();
  }

  /**
   * A GWT-swapped version of test rule interface that does nothing. All methods extended from
   * {@link org.junit.rules.TestRule} needs to be stripped.
   */
  interface JUnitTestRule {}

  static final String EXPECT_FAILURE_WARNING_IF_GWT =
      " Note: One possible reason for a failure not to be caught is for the test to throw some "
          + "other exception before the failure would have happened. Under GWT, such an exception "
          + "is hidden by this message. The non-GWT tests do not have this problem, so you may "
          + "wish to debug them first. If you're still having this problem, consider temporarily "
          + "modifying the GWT copy of PlatformBaseSubjectTestCase to remove the call to "
          + "ensureFailureCaught(). Removing that call will let any other exception fall through. "
          + "(But of course it will also prevent the test from verifying that the expected failure "
          + "occurred.)";

  // TODO(user): Move this logic to a common location.
  private static NativeRegExp compile(String pattern) {
    return new NativeRegExp(pattern);
  }

  @JsType(isNative = true, name = "RegExp", namespace = GLOBAL)
  private static class NativeRegExp {
    public NativeRegExp(String pattern) {}

    public native boolean test(String input);
  }

  @JsType(isNative = true, name = "Number", namespace = GLOBAL)
  private interface Number {
    String toLocaleString(Object locales, ToLocaleStringOptions options);
  }

  @JsType(isNative = true, name = "?", namespace = GLOBAL) // "structural type"; see JsType Javadoc
  private interface ToLocaleStringOptions {
    @JsProperty
    int getMinimumFractionDigits();

    @JsProperty
    int getMaximumFractionDigits();

    @JsProperty
    boolean getUseGrouping();
  }

  private static final class JavaLikeOptions implements ToLocaleStringOptions {
    private static final ToLocaleStringOptions INSTANCE = new JavaLikeOptions();

    @Override
    public int getMinimumFractionDigits() {
      return 1;
    }

    @Override
    public int getMaximumFractionDigits() {
      return 20;
    }

    @Override
    public boolean getUseGrouping() {
      return false;
    }
  }
}
