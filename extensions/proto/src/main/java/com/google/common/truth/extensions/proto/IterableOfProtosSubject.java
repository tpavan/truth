/*
 * Copyright (c) 2016 Google, Inc.
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

package com.google.common.truth.extensions.proto;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.asList;
import static com.google.common.truth.extensions.proto.FieldScopeUtil.asList;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Ordered;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.Arrays;
import java.util.Comparator;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Truth subject for the iterables of protocol buffers.
 *
 * <p>{@code ProtoTruth.assertThat(actual).containsExactly(expected)} performs the same assertion as
 * {@code Truth.assertThat(actual).containsExactly(expected)}. By default, the assertions are strict
 * with respect to repeated field order, missing fields, etc. This behavior can be changed with the
 * configuration methods on this subject, e.g. {@code
 * ProtoTruth.assertThat(actual).ignoringRepeatedFieldOrder().containsExactlyEntriesIn(expected)}.
 *
 * <p>Floating-point fields are compared using exact equality, which is <a
 * href="http://google.github.io/truth/floating_point">probably not what you want</a> if the values
 * are the results of some arithmetic. Support for approximate equality may be added in a later
 * version.
 *
 * <p>Equality tests, and other methods, may yield slightly different behavior for versions 2 and 3
 * of Protocol Buffers. If testing protos of multiple versions, make sure you understand the
 * behaviors of default and unknown fields so you don't under or over test.
 */
public class IterableOfProtosSubject<
        S extends IterableOfProtosSubject<S, M, C>, M extends Message, C extends Iterable<M>>
    extends Subject<S, C> {

  private final FluentEqualityConfig config;

  /** Default implementation of {@link IterableOfProtosSubject}. */
  public static final class IterableOfMessagesSubject<M extends Message>
      extends IterableOfProtosSubject<IterableOfMessagesSubject<M>, M, Iterable<M>> {
    // Fun generics note! Theoretically we shouldn't have to expose the IterableOfMessagesSubject
    // type at all, but it seems that Java generics handling is either insufficiently intelligent,
    // or beyond my understanding. If we type the iterablesOfProtos() method with the return
    // signature:
    //   <M extends Message> IterableOfProtosSubject.Factory<?, M, Iterable<M>>
    //
    // Then this does not compile, due to type bound conflicts:
    //   Truth.assertAbout(IterableOfProtosSubject.<M>iterablesOfProtos())
    //       .that(listOfProtos)
    //       .contains(foo);
    //
    // But *this* compiles fine!:
    //   IterableOfProtosSubject.Factory<?, M, Iterable<M>> factory =
    //       IterableOfProtosSubject.<M>iterablesOfProtos()
    //   Truth.assertAbout(factory).that(listOfProtos).contains(foo);
    //
    // It seems that when the wild card is captured through inlining, as opposed to being lost and
    // generalized through reference assignment, javac stops being able to intuit that the '?' in
    // 'Factory<?, M, Iterable<M>>' does in fact satisfy the type bound of 'S' in
    // 'S extends Subject<S, T>', even though this is evident from the definition of Factory and
    // from IterableOfProtosSubject.
    //
    // The work around would be annoyingly verbose for users, so we expose IterableOfMessagesSubject
    // explicitly so that there are no wildcards to have conflicting bounds.

    IterableOfMessagesSubject(FailureMetadata failureMetadata, @NullableDecl Iterable<M> messages) {
      super(failureMetadata, messages);
    }

    private IterableOfMessagesSubject(
        FailureMetadata failureMetadata,
        FluentEqualityConfig config,
        @NullableDecl Iterable<M> messages) {
      super(failureMetadata, config, messages);
    }
  }

  static <M extends Message>
      Subject.Factory<IterableOfMessagesSubject<M>, Iterable<M>> iterableOfMessages(
          final FluentEqualityConfig config) {
    return new Subject.Factory<IterableOfMessagesSubject<M>, Iterable<M>>() {
      @Override
      public IterableOfMessagesSubject<M> createSubject(
          FailureMetadata metadata, Iterable<M> actual) {
        return new IterableOfMessagesSubject<M>(metadata, config, actual);
      }
    };
  }

  protected IterableOfProtosSubject(FailureMetadata failureMetadata, @NullableDecl C messages) {
    this(failureMetadata, FluentEqualityConfig.defaultInstance(), messages);
  }

  IterableOfProtosSubject(
      FailureMetadata failureMetadata, FluentEqualityConfig config, @NullableDecl C messages) {
    super(failureMetadata, messages);
    this.config = config;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // IterableSubject methods
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private final IterableSubject delegate() {
    IterableSubject delegate = check().that(actual());
    if (internalCustomName() != null) {
      delegate = delegate.named(internalCustomName());
    }
    return delegate;
  }

  /** Fails if the subject is not empty. */
  public void isEmpty() {
    delegate().isEmpty();
  }

  /** Fails if the subject is empty. */
  public void isNotEmpty() {
    delegate().isNotEmpty();
  }

  /** Fails if the subject does not have the given size. */
  public void hasSize(int expectedSize) {
    delegate().hasSize(expectedSize);
  }

  /** Checks (with a side-effect failure) that the subject contains the supplied item. */
  public void contains(@NullableDecl Object element) {
    delegate().contains(element);
  }

  /** Checks (with a side-effect failure) that the subject does not contain the supplied item. */
  public void doesNotContain(@NullableDecl Object element) {
    delegate().doesNotContain(element);
  }

  /** Checks that the subject does not contain duplicate elements. */
  public void containsNoDuplicates() {
    delegate().containsNoDuplicates();
  }

  /** Checks that the subject contains at least one of the provided objects or fails. */
  public void containsAnyOf(
      @NullableDecl Object first, @NullableDecl Object second, @NullableDecl Object... rest) {
    delegate().containsAnyOf(first, second, rest);
  }

  /**
   * Checks that the subject contains at least one of the objects contained in the provided
   * collection or fails.
   */
  public void containsAnyIn(Iterable<?> expected) {
    delegate().containsAnyIn(expected);
  }

  /**
   * Checks that the subject contains at least one of the objects contained in the provided array or
   * fails.
   */
  public void containsAnyIn(Object[] expected) {
    delegate().containsAnyIn(expected);
  }

  /**
   * Checks that the actual iterable contains at least all of the expected elements or fails. If an
   * element appears more than once in the expected elements to this call then it must appear at
   * least that number of times in the actual elements.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method. The expected elements must appear in the given order
   * within the actual elements, but they are not required to be consecutive.
   */
  @CanIgnoreReturnValue
  public Ordered containsAllOf(
      @NullableDecl Object firstExpected,
      @NullableDecl Object secondExpected,
      @NullableDecl Object... restOfExpected) {
    return delegate().containsAllOf(firstExpected, secondExpected, restOfExpected);
  }

  /**
   * Checks that the actual iterable contains at least all of the expected elements or fails. If an
   * element appears more than once in the expected elements then it must appear at least that
   * number of times in the actual elements.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method. The expected elements must appear in the given order
   * within the actual elements, but they are not required to be consecutive.
   */
  @CanIgnoreReturnValue
  public Ordered containsAllIn(Iterable<?> expected) {
    return delegate().containsAllIn(expected);
  }

  /**
   * Checks that the actual iterable contains at least all of the expected elements or fails. If an
   * element appears more than once in the expected elements then it must appear at least that
   * number of times in the actual elements.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method. The expected elements must appear in the given order
   * within the actual elements, but they are not required to be consecutive.
   */
  @CanIgnoreReturnValue
  public Ordered containsAllIn(Object[] expected) {
    return delegate().containsAllIn(expected);
  }

  /**
   * Checks that a subject contains exactly the provided objects or fails.
   *
   * <p>Multiplicity is respected. For example, an object duplicated exactly 3 times in the
   * parameters asserts that the object must likewise be duplicated exactly 3 times in the subject.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method.
   *
   * <p>To test that the iterable contains the same elements as an array, prefer {@link
   * #containsExactlyElementsIn(Object[])}. It makes clear that the given array is a list of
   * elements, not an element itself. This helps human readers and avoids a compiler warning.
   */
  @CanIgnoreReturnValue
  public Ordered containsExactly(@NullableDecl Object... varargs) {
    return delegate().containsExactly(varargs);
  }

  /**
   * Checks that a subject contains exactly the provided objects or fails.
   *
   * <p>Multiplicity is respected. For example, an object duplicated exactly 3 times in the {@code
   * Iterable} parameter asserts that the object must likewise be duplicated exactly 3 times in the
   * subject.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method.
   */
  @CanIgnoreReturnValue
  public Ordered containsExactlyElementsIn(Iterable<?> expected) {
    return delegate().containsExactlyElementsIn(expected);
  }

  /**
   * Checks that a subject contains exactly the provided objects or fails.
   *
   * <p>Multiplicity is respected. For example, an object duplicated exactly 3 times in the {@code
   * Iterable} parameter asserts that the object must likewise be duplicated exactly 3 times in the
   * subject.
   *
   * <p>To also test that the contents appear in the given order, make a call to {@code inOrder()}
   * on the object returned by this method.
   */
  @CanIgnoreReturnValue
  public Ordered containsExactlyElementsIn(Object[] expected) {
    return delegate().containsExactlyElementsIn(expected);
  }

  /**
   * Checks that a actual iterable contains none of the excluded objects or fails. (Duplicates are
   * irrelevant to this test, which fails if any of the actual elements equal any of the excluded.)
   */
  public void containsNoneOf(
      @NullableDecl Object firstExcluded,
      @NullableDecl Object secondExcluded,
      @NullableDecl Object... restOfExcluded) {
    delegate().containsNoneOf(firstExcluded, secondExcluded, restOfExcluded);
  }

  /**
   * Checks that a actual iterable contains none of the elements contained in the excluded iterable
   * or fails. (Duplicates are irrelevant to this test, which fails if any of the actual elements
   * equal any of the excluded.)
   */
  public void containsNoneIn(Iterable<?> excluded) {
    delegate().containsNoneIn(excluded);
  }

  /**
   * Checks that a actual iterable contains none of the elements contained in the excluded iterable
   * or fails. (Duplicates are irrelevant to this test, which fails if any of the actual elements
   * equal any of the excluded.)
   */
  public void containsNoneIn(Object[] excluded) {
    delegate().containsNoneIn(excluded);
  }

  // Messages don't have a natural order so we do not provide the no-args variant of
  // isStrictlyOrdered, or isOrdered. If we move to inheritance, the methods should be deprecated in
  // this class.

  /**
   * Fails if the iterable is not strictly ordered, according to the given comparator. Strictly
   * ordered means that each element in the iterable is <i>strictly</i> greater than the element
   * that preceded it.
   *
   * @throws ClassCastException if any pair of elements is not mutually Comparable
   */
  public void isStrictlyOrdered(Comparator<?> comparator) {
    delegate().isStrictlyOrdered(comparator);
  }

  /**
   * Fails if the iterable is not ordered, according to the given comparator. Ordered means that
   * each element in the iterable is greater than or equal to the element that preceded it.
   *
   * @throws ClassCastException if any pair of elements is not mutually Comparable
   */
  public void isOrdered(Comparator<?> comparator) {
    delegate().isOrdered(comparator);
  }

  /**
   * Starts a method chain for a check in which the actual elements (i.e. the elements of the {@link
   * Iterable} under test) are compared to expected elements using the given {@link Correspondence}.
   * The actual elements must be of type {@code A}, the expected elements must be of type {@code E}.
   * The check is actually executed by continuing the method chain. For example:
   *
   * <pre>{@code
   * assertThat(actualIterable).comparingElementsUsing(correspondence).contains(expected);
   * }</pre>
   *
   * where {@code actualIterable} is an {@code Iterable<A>} (or, more generally, an {@code
   * Iterable<? extends A>}), {@code correspondence} is a {@code Correspondence<A, E>}, and {@code
   * expected} is an {@code E}.
   *
   * <p>Any of the methods on the returned object may throw {@link ClassCastException} if they
   * encounter an actual element that is not of type {@code A}.
   *
   * <p>Note that the {@code IterableOfProtosSubject} is designed to save you from having to write
   * your own {@link Correspondence}. The configuration methods, such as {@link
   * #ignoringRepeatedFieldOrder()} will construct a {@link Correspondence} under the hood which
   * performs protobuf comparisons with {@link #ignoringRepeatedFieldOrder()}.
   */
  public <A, E> IterableSubject.UsingCorrespondence<A, E> comparingElementsUsing(
      Correspondence<A, E> correspondence) {
    return delegate().comparingElementsUsing(correspondence);
  }

  /**
   * Specifies a way to pair up unexpected and missing elements in the message when an assertion
   * fails. For example:
   *
   * <pre>{@code
   * assertThat(actualFoos)
   *     .ignoringRepeatedFieldOrder()
   *     .ignoringFields(Foo.BAR_FIELD_NUMBER)
   *     .displayingDiffsPairedBy(Foo::getId)
   *     .containsExactlyElementsIn(expectedFoos);
   * }</pre>
   *
   * <p>On assertions where it makes sense to do so, the elements are paired as follows: they are
   * keyed by {@code keyFunction}, and if an unexpected element and a missing element have the same
   * non-null key then the they are paired up. (Elements with null keys are not paired.) The failure
   * message will show paired elements together, and a diff will be shown.
   *
   * <p>The expected elements given in the assertion should be uniquely keyed by {@link
   * keyFunction}. If multiple missing elements have the same key then the pairing will be skipped.
   *
   * <p>Useful key functions will have the property that key equality is less strict than the
   * already specified equality rules; i.e. given {@code actual} and {@code expected} values with
   * keys {@code actualKey} and {@code expectedKey}, if {@code actual} and {@code expected} compare
   * equal given the rest of the directives such as {@code ignoringRepeatedFieldOrder} and {@code
   * ignoringFields}, then it is guaranteed that {@code actualKey} is equal to {@code expectedKey},
   * but there are cases where {@code actualKey} is equal to {@code expectedKey} but the direct
   * comparison fails.
   *
   * <p>Note that calling this method makes no difference to whether a test passes or fails, it just
   * improves the message if it fails.
   */
  public IterableOfProtosUsingCorrespondence<M> displayingDiffsPairedBy(
      Function<? super M, ?> keyFunction) {
    return usingCorrespondence().displayingDiffsPairedBy(keyFunction);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // ProtoFluentAssertion Configuration
  //////////////////////////////////////////////////////////////////////////////////////////////////

  IterableOfProtosFluentAssertion<M> usingConfig(FluentEqualityConfig newConfig) {
    Subject.Factory<IterableOfMessagesSubject<M>, Iterable<M>> factory =
        iterableOfMessages(newConfig);
    IterableOfMessagesSubject<M> newSubject = check().about(factory).that(actual());
    if (internalCustomName() != null) {
      newSubject = newSubject.named(internalCustomName());
    }
    return new IterableOfProtosFluentAssertionImpl<M>(newSubject);
  }

  /**
   * Specifies that the 'has' bit of individual fields should be ignored when comparing for
   * equality.
   *
   * <p>For version 2 Protocol Buffers, this setting determines whether two protos with the same
   * value for a primitive field compare equal if one explicitly sets the value, and the other
   * merely implicitly uses the schema-defined default. This setting also determines whether unknown
   * fields should be considered in the comparison. By {@code ignoringFieldAbsence()}, unknown
   * fields are ignored, and value-equal fields as specified above are considered equal.
   *
   * <p>For version 3 Protocol Buffers, this setting has no effect. Primitive fields set to their
   * default value are indistinguishable from unset fields in proto 3. Proto 3 also eliminates
   * unknown fields, so this setting has no effect there either.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFieldAbsence() {
    return usingConfig(config.ignoringFieldAbsence());
  }

  /**
   * Specifies that the ordering of repeated fields, at all levels, should be ignored when comparing
   * for equality.
   *
   * <p>This setting applies to all repeated fields recursively, but it does not ignore structure.
   * For example, with {@link #ignoringRepeatedFieldOrder()}, a repeated {@code int32} field {@code
   * bar}, set inside a repeated message field {@code foo}, the following protos will all compare
   * equal:
   *
   * <pre>{@code
   * message1: {
   *   foo: {
   *     bar: 1
   *     bar: 2
   *   }
   *   foo: {
   *     bar: 3
   *     bar: 4
   *   }
   * }
   *
   * message2: {
   *   foo: {
   *     bar: 2
   *     bar: 1
   *   }
   *   foo: {
   *     bar: 4
   *     bar: 3
   *   }
   * }
   *
   * message3: {
   *   foo: {
   *     bar: 4
   *     bar: 3
   *   }
   *   foo: {
   *     bar: 2
   *     bar: 1
   *   }
   * }
   * }</pre>
   *
   * <p>However, the following message will compare equal to none of these:
   *
   * <pre>{@code
   * message4: {
   *   foo: {
   *     bar: 1
   *     bar: 3
   *   }
   *   foo: {
   *     bar: 2
   *     bar: 4
   *   }
   * }
   * }</pre>
   *
   * <p>This setting does not apply to map fields, for which field order is always ignored. The
   * serialization order of map fields is undefined, and it may change from runtime to runtime.
   */
  public IterableOfProtosFluentAssertion<M> ignoringRepeatedFieldOrder() {
    return usingConfig(config.ignoringRepeatedFieldOrder());
  }

  /**
   * Specifies that, for all repeated and map fields, any elements in the 'actual' proto which are
   * not found in the 'expected' proto are ignored, with the exception of fields in the expected
   * proto which are empty. To ignore empty repeated fields as well, use {@link
   * #comparingExpectedFieldsOnly}.
   *
   * <p>This rule is applied independently from {@link #ignoringRepeatedFieldOrder}. If ignoring
   * repeated field order AND extra repeated field elements, all that is tested is that the expected
   * elements comprise a subset of the actual elements. If not ignoring repeated field order, but
   * still ignoring extra repeated field elements, the actual elements must contain a subsequence
   * that matches the expected elements for the test to pass. (The subsequence rule does not apply
   * to Map fields, which are always compared by key.)
   */
  public IterableOfProtosFluentAssertion<M> ignoringExtraRepeatedFieldElements() {
    return usingConfig(config.ignoringExtraRepeatedFieldElements());
  }

  /**
   * Compares double fields as equal if they are both finite and their absolute difference is less
   * than or equal to {@code tolerance}.
   *
   * @param tolerance A finite, non-negative tolerance.
   */
  public IterableOfProtosFluentAssertion<M> usingDoubleTolerance(double tolerance) {
    return usingConfig(config.usingDoubleTolerance(tolerance));
  }

  /**
   * Compares float fields as equal if they are both finite and their absolute difference is less
   * than or equal to {@code tolerance}.
   *
   * @param tolerance A finite, non-negative tolerance.
   */
  public IterableOfProtosFluentAssertion<M> usingFloatTolerance(float tolerance) {
    return usingConfig(config.usingFloatTolerance(tolerance));
  }

  /**
   * Limits the comparison of Protocol buffers to the fields set in the expected proto(s). When
   * multiple protos are specified, the comparison is limited to the union of set fields in all the
   * expected protos.
   *
   * <p>The "expected proto(s)" are those passed to the method in {@link
   * IterableOfProtosUsingCorrespondence} at the end of the call-chain.
   *
   * <p>Fields not set in the expected proto(s) are ignored. In particular, proto3 fields which have
   * their default values are ignored, as these are indistinguishable from unset fields. If you want
   * to assert that a proto3 message has certain fields with default values, you cannot use this
   * method.
   */
  public IterableOfProtosFluentAssertion<M> comparingExpectedFieldsOnly() {
    return usingConfig(config.comparingExpectedFieldsOnly());
  }

  /**
   * Limits the comparison of Protocol buffers to the defined {@link FieldScope}.
   *
   * <p>This method is additive and has well-defined ordering semantics. If the invoking {@link
   * ProtoFluentAssertion} is already scoped to a {@link FieldScope} {@code X}, and this method is
   * invoked with {@link FieldScope} {@code Y}, the resultant {@link ProtoFluentAssertion} is
   * constrained to the intersection of {@link FieldScope}s {@code X} and {@code Y}.
   *
   * <p>By default, {@link ProtoFluentAssertion} is constrained to {@link FieldScopes#all()}, that
   * is, no fields are excluded from comparison.
   */
  public IterableOfProtosFluentAssertion<M> withPartialScope(FieldScope fieldScope) {
    return usingConfig(config.withPartialScope(checkNotNull(fieldScope, "fieldScope")));
  }

  /**
   * Excludes the top-level message fields with the given tag numbers from the comparison.
   *
   * <p>This method adds on any previous {@link FieldScope} related settings, overriding previous
   * changes to ensure the specified fields are ignored recursively. All sub-fields of these field
   * numbers are ignored, and all sub-messages of type {@code M} will also have these field numbers
   * ignored.
   *
   * <p>If an invalid field number is supplied, the terminal comparison operation will throw a
   * runtime exception.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFields(int firstFieldNumber, int... rest) {
    return ignoringFields(asList(firstFieldNumber, rest));
  }

  /**
   * Excludes the top-level message fields with the given tag numbers from the comparison.
   *
   * <p>This method adds on any previous {@link FieldScope} related settings, overriding previous
   * changes to ensure the specified fields are ignored recursively. All sub-fields of these field
   * numbers are ignored, and all sub-messages of type {@code M} will also have these field numbers
   * ignored.
   *
   * <p>If an invalid field number is supplied, the terminal comparison operation will throw a
   * runtime exception.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFields(Iterable<Integer> fieldNumbers) {
    return usingConfig(config.ignoringFields(fieldNumbers));
  }

  /**
   * Excludes all message fields matching the given {@link FieldDescriptor}s from the comparison.
   *
   * <p>This method adds on any previous {@link FieldScope} related settings, overriding previous
   * changes to ensure the specified fields are ignored recursively. All sub-fields of these field
   * descriptors are ignored, no matter where they occur in the tree.
   *
   * <p>If a field descriptor which does not, or cannot occur in the proto structure is supplied, it
   * is silently ignored.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFieldDescriptors(
      FieldDescriptor firstFieldDescriptor, FieldDescriptor... rest) {
    return ignoringFieldDescriptors(asList(firstFieldDescriptor, rest));
  }

  /**
   * Excludes all message fields matching the given {@link FieldDescriptor}s from the comparison.
   *
   * <p>This method adds on any previous {@link FieldScope} related settings, overriding previous
   * changes to ensure the specified fields are ignored recursively. All sub-fields of these field
   * descriptors are ignored, no matter where they occur in the tree.
   *
   * <p>If a field descriptor which does not, or cannot occur in the proto structure is supplied, it
   * is silently ignored.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFieldDescriptors(
      Iterable<FieldDescriptor> fieldDescriptors) {
    return usingConfig(config.ignoringFieldDescriptors(fieldDescriptors));
  }

  /**
   * Excludes all specific field paths under the argument {@link FieldScope} from the comparison.
   *
   * <p>This method is additive and has well-defined ordering semantics. If the invoking {@link
   * ProtoFluentAssertion} is already scoped to a {@link FieldScope} {@code X}, and this method is
   * invoked with {@link FieldScope} {@code Y}, the resultant {@link ProtoFluentAssertion} is
   * constrained to the subtraction of {@code X - Y}.
   *
   * <p>By default, {@link ProtoFluentAssertion} is constrained to {@link FieldScopes#all()}, that
   * is, no fields are excluded from comparison.
   */
  public IterableOfProtosFluentAssertion<M> ignoringFieldScope(FieldScope fieldScope) {
    return usingConfig(config.ignoringFieldScope(checkNotNull(fieldScope, "fieldScope")));
  }

  /**
   * If set, in the event of a comparison failure, the error message printed will list only those
   * specific fields that did not match between the actual and expected values. Useful for very
   * large protocol buffers.
   *
   * <p>This a purely cosmetic setting, and it has no effect on the behavior of the test.
   */
  public IterableOfProtosFluentAssertion<M> reportingMismatchesOnly() {
    return usingConfig(config.reportingMismatchesOnly());
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // UsingCorrespondence Methods
  //////////////////////////////////////////////////////////////////////////////////////////////////

  // A forwarding implementation of IterableSubject.UsingCorrespondence which passes the expected
  // protos to FluentEqualityConfig before comparing.  This is required to support
  // displayingDiffsPairedBy(), since we can't pass the user to a vanilla
  // IterableSubject.UsingCorrespondence until we know what the expected messages are.
  private static class UsingCorrespondence<M extends Message>
      implements IterableOfProtosUsingCorrespondence<M> {
    private final IterableOfProtosSubject<?, M, ?> subject;
    @NullableDecl private final Function<? super M, ? extends Object> keyFunction;

    UsingCorrespondence(
        IterableOfProtosSubject<?, M, ?> subject,
        @NullableDecl Function<? super M, ? extends Object> keyFunction) {
      this.subject = checkNotNull(subject);
      this.keyFunction = keyFunction;
    }

    private IterableSubject.UsingCorrespondence<M, M> delegate(Iterable<? extends M> messages) {
      IterableSubject.UsingCorrespondence<M, M> usingCorrespondence =
          subject.comparingElementsUsing(
              subject
                  .config
                  .withExpectedMessages(messages)
                  .<M>toCorrespondence(FieldScopeUtil.getSingleDescriptor(subject.actual())));
      if (keyFunction != null) {
        usingCorrespondence = usingCorrespondence.displayingDiffsPairedBy(keyFunction);
      }
      return usingCorrespondence;
    }

    @Override
    public IterableOfProtosUsingCorrespondence<M> displayingDiffsPairedBy(
        Function<? super M, ?> keyFunction) {
      return new UsingCorrespondence<M>(subject, checkNotNull(keyFunction));
    }

    @Override
    public void contains(@NullableDecl M expected) {
      delegate(Arrays.asList(expected)).contains(expected);
    }

    @Override
    public void doesNotContain(@NullableDecl M excluded) {
      delegate(Arrays.asList(excluded)).doesNotContain(excluded);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsExactly(@NullableDecl M... expected) {
      return delegate(Arrays.asList(expected)).containsExactly(expected);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsExactlyElementsIn(Iterable<? extends M> expected) {
      return delegate(expected).containsExactlyElementsIn(expected);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsExactlyElementsIn(M[] expected) {
      return delegate(Arrays.asList(expected)).containsExactlyElementsIn(expected);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsAllOf(
        @NullableDecl M first, @NullableDecl M second, @NullableDecl M... rest) {
      return delegate(Lists.asList(first, second, rest)).containsAllOf(first, second, rest);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsAllIn(Iterable<? extends M> expected) {
      return delegate(expected).containsAllIn(expected);
    }

    @Override
    @CanIgnoreReturnValue
    public Ordered containsAllIn(M[] expected) {
      return delegate(Arrays.asList(expected)).containsAllIn(expected);
    }

    @Override
    public void containsAnyOf(
        @NullableDecl M first, @NullableDecl M second, @NullableDecl M... rest) {
      delegate(Lists.asList(first, second, rest)).containsAnyOf(first, second, rest);
    }

    @Override
    public void containsAnyIn(Iterable<? extends M> expected) {
      delegate(expected).containsAnyIn(expected);
    }

    @Override
    public void containsAnyIn(M[] expected) {
      delegate(Arrays.asList(expected)).containsAnyIn(expected);
    }

    @Override
    public void containsNoneOf(
        @NullableDecl M firstExcluded,
        @NullableDecl M secondExcluded,
        @NullableDecl M... restOfExcluded) {
      delegate(Lists.asList(firstExcluded, secondExcluded, restOfExcluded))
          .containsNoneOf(firstExcluded, secondExcluded, restOfExcluded);
    }

    @Override
    public void containsNoneIn(Iterable<? extends M> excluded) {
      delegate(excluded).containsNoneIn(excluded);
    }

    @Override
    public void containsNoneIn(M[] excluded) {
      delegate(Arrays.asList(excluded)).containsNoneIn(excluded);
    }
  }

  private IterableOfProtosUsingCorrespondence<M> usingCorrespondence() {
    return new UsingCorrespondence<M>(this, /* keyFunction= */ null);
  }

  // The UsingCorrespondence methods have conflicting erasure with default IterableSubject methods,
  // so we can't implement them both on the same class, but we want to define both so
  // IterableOfProtosSubjects are interchangeable with IterableSubjects when no configuration is
  // specified. So, we implement a dumb, private delegator to return instead.
  private static final class IterableOfProtosFluentAssertionImpl<M extends Message>
      implements IterableOfProtosFluentAssertion<M> {
    private final IterableOfProtosSubject<?, M, ?> subject;

    IterableOfProtosFluentAssertionImpl(IterableOfProtosSubject<?, M, ?> subject) {
      this.subject = subject;
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFieldAbsence() {
      return subject.ignoringFieldAbsence();
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringRepeatedFieldOrder() {
      return subject.ignoringRepeatedFieldOrder();
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringExtraRepeatedFieldElements() {
      return subject.ignoringExtraRepeatedFieldElements();
    }

    @Override
    public IterableOfProtosFluentAssertion<M> usingDoubleTolerance(double tolerance) {
      return subject.usingDoubleTolerance(tolerance);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> usingFloatTolerance(float tolerance) {
      return subject.usingFloatTolerance(tolerance);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> comparingExpectedFieldsOnly() {
      return subject.comparingExpectedFieldsOnly();
    }

    @Override
    public IterableOfProtosFluentAssertion<M> withPartialScope(FieldScope fieldScope) {
      return subject.withPartialScope(fieldScope);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFields(int firstFieldNumber, int... rest) {
      return subject.ignoringFields(firstFieldNumber, rest);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFields(Iterable<Integer> fieldNumbers) {
      return subject.ignoringFields(fieldNumbers);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFieldDescriptors(
        FieldDescriptor firstFieldDescriptor, FieldDescriptor... rest) {
      return subject.ignoringFieldDescriptors(firstFieldDescriptor, rest);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFieldDescriptors(
        Iterable<FieldDescriptor> fieldDescriptors) {
      return subject.ignoringFieldDescriptors(fieldDescriptors);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> ignoringFieldScope(FieldScope fieldScope) {
      return subject.ignoringFieldScope(fieldScope);
    }

    @Override
    public IterableOfProtosFluentAssertion<M> reportingMismatchesOnly() {
      return subject.reportingMismatchesOnly();
    }

    @Override
    public IterableOfProtosUsingCorrespondence<M> displayingDiffsPairedBy(
        Function<? super M, ?> keyFunction) {
      return usingCorrespondence().displayingDiffsPairedBy(keyFunction);
    }

    @Override
    public void contains(@NullableDecl M expected) {
      usingCorrespondence().contains(expected);
    }

    @Override
    public void doesNotContain(@NullableDecl M excluded) {
      usingCorrespondence().doesNotContain(excluded);
    }

    @Override
    public Ordered containsExactly(@NullableDecl M... expected) {
      return usingCorrespondence().containsExactly(expected);
    }

    @Override
    public Ordered containsExactlyElementsIn(Iterable<? extends M> expected) {
      return usingCorrespondence().containsExactlyElementsIn(expected);
    }

    @Override
    public Ordered containsExactlyElementsIn(M[] expected) {
      return usingCorrespondence().containsExactlyElementsIn(expected);
    }

    @Override
    public Ordered containsAllOf(
        @NullableDecl M first, @NullableDecl M second, @NullableDecl M... rest) {
      return usingCorrespondence().containsAllOf(first, second, rest);
    }

    @Override
    public Ordered containsAllIn(Iterable<? extends M> expected) {
      return usingCorrespondence().containsAllIn(expected);
    }

    @Override
    public Ordered containsAllIn(M[] expected) {
      return usingCorrespondence().containsAllIn(expected);
    }

    @Override
    public void containsAnyOf(
        @NullableDecl M first, @NullableDecl M second, @NullableDecl M... rest) {
      usingCorrespondence().containsAnyOf(first, second, rest);
    }

    @Override
    public void containsAnyIn(Iterable<? extends M> expected) {
      usingCorrespondence().containsAnyIn(expected);
    }

    @Override
    public void containsAnyIn(M[] expected) {
      usingCorrespondence().containsAnyIn(expected);
    }

    @Override
    public void containsNoneOf(
        @NullableDecl M firstExcluded,
        @NullableDecl M secondExcluded,
        @NullableDecl M... restOfExcluded) {
      usingCorrespondence().containsNoneOf(firstExcluded, secondExcluded, restOfExcluded);
    }

    @Override
    public void containsNoneIn(Iterable<? extends M> excluded) {
      usingCorrespondence().containsNoneIn(excluded);
    }

    @Override
    public void containsNoneIn(M[] excluded) {
      usingCorrespondence().containsNoneIn(excluded);
    }

    @Override
    @Deprecated
    public boolean equals(Object o) {
      return subject.equals(o);
    }

    @Override
    @Deprecated
    public int hashCode() {
      return subject.hashCode();
    }

    private final IterableOfProtosUsingCorrespondence<M> usingCorrespondence() {
      return subject.usingCorrespondence();
    }
  }
}
