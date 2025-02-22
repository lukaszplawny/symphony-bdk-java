package com.symphony.bdk.core.retry;

import com.symphony.bdk.core.config.model.BdkRetryConfig;
import com.symphony.bdk.core.retry.resilience4j.Resilience4jRetryWithRecovery;
import com.symphony.bdk.core.util.function.ConsumerWithThrowable;
import com.symphony.bdk.core.util.function.SupplierWithApiException;
import com.symphony.bdk.http.api.ApiException;

import org.apiguardian.api.API;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Builder class to facilitate the instantiation of a {@link RetryWithRecovery}.
 *
 * @param <T> the type to be returned by {@link RetryWithRecovery#execute()}.
 */
@API(status = API.Status.INTERNAL)
public class RetryWithRecoveryBuilder<T> {
  private String name;
  private String address;
  private BdkRetryConfig retryConfig;
  private SupplierWithApiException<T> supplier;
  private Predicate<Throwable> retryOnExceptionPredicate;
  private Predicate<Exception> ignoreException;
  private List<RecoveryStrategy> recoveryStrategies;

  /**
   * Copies all fields of an existing builder except the {@link #supplier}.
   *
   * @param from the {@link RetryWithRecovery} to be copied.
   * @param <T>  the target parametrized type.
   * @return a copy of the builder passed as parameter.
   */
  public static <T> RetryWithRecoveryBuilder<T> from(RetryWithRecoveryBuilder<?> from) {
    RetryWithRecoveryBuilder<T> copy = new RetryWithRecoveryBuilder();
    copy.name = from.name;
    copy.address = from.address;
    copy.retryConfig = from.retryConfig;
    copy.retryOnExceptionPredicate = from.retryOnExceptionPredicate;
    copy.ignoreException = from.ignoreException;
    copy.recoveryStrategies = new ArrayList<>(from.recoveryStrategies);

    return copy;
  }

  public static <T> RetryWithRecoveryBuilder<T> copyWithoutRecoveryStrategies(RetryWithRecoveryBuilder<?> from) {
    RetryWithRecoveryBuilder<T> copy = new RetryWithRecoveryBuilder();
    copy.name = from.name;
    copy.address = from.address;
    copy.retryConfig = from.retryConfig;
    copy.retryOnExceptionPredicate = from.retryOnExceptionPredicate;
    copy.ignoreException = from.ignoreException;

    return copy;
  }

  /**
   * Checks if a throwable is a network issue or a {@link ApiException} minor error.
   * This is the default function used in {@link RetryWithRecovery}
   * to check if a given exception thrown should lead to a retry.
   *
   * @param t the throwable to be checked.
   * @return true if passed throwable is either a {@link SocketTimeoutException} or {@link ConnectException}
   * or if it is a {@link ApiException} which {@link ApiException#isServerError()}
   * or {@link ApiException#isUnauthorized()} or {@link ApiException#isTooManyRequestsError()}.
   */
  public static boolean isNetworkOrMinorError(Throwable t) {
    if (t instanceof ApiException) {
      ApiException apiException = (ApiException) t;
      return apiException.isServerError() || apiException.isUnauthorized() || apiException.isTooManyRequestsError();
    }
    return t.getCause() instanceof SocketTimeoutException || t.getCause() instanceof ConnectException;
  }

  /**
   * Checks if a throwable is a network issue or a {@link ApiException} minor error or client error.
   *
   * @param t the throwable to be checked.
   * @return true if passed throwable is either a {@link SocketTimeoutException}
   * or {@link ConnectException}
   * or {@link UnknownHostException}
   * or if it is a {@link ApiException} which {@link ApiException#isServerError()}
   * or {@link ApiException#isUnauthorized()} or {@link ApiException#isTooManyRequestsError()}
   * or {@link ApiException#isClientError()}.
   */
  public static boolean isNetworkOrMinorErrorOrClientError(Throwable t) {
    if (t instanceof ApiException) {
      ApiException apiException = (ApiException) t;
      return apiException.isServerError() || apiException.isUnauthorized() || apiException.isTooManyRequestsError()
          || apiException.isClientError();
    }
    // keep the datafeed loop running for errors that might be temporary network errors
    return t.getCause() instanceof SocketTimeoutException
        || t.getCause() instanceof ConnectException
        || t.getCause() instanceof UnknownHostException;
  }

  /**
   * Default constructor which ignores no exception
   * and retries exceptions fulfilling {@link RetryWithRecoveryBuilder#isNetworkOrMinorError}.
   */
  public RetryWithRecoveryBuilder() {
    this.recoveryStrategies = new ArrayList<>();
    this.ignoreException = e -> false;
    this.retryOnExceptionPredicate = RetryWithRecoveryBuilder::isNetworkOrMinorError;
    this.retryConfig = new BdkRetryConfig();
  }

  /**
   * Sets the name and returns the modified builder.
   *
   * @param name the name of the {@link RetryWithRecovery}
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the retry configuration and returns the modified builder.
   *
   * @param retryConfig the retry configuration to be used.
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> retryConfig(BdkRetryConfig retryConfig) {
    this.retryConfig = retryConfig;
    return this;
  }

  /**
   * Sets the address and returns the modified builder.
   *
   * @param address that we are trying to reach.
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> basePath(String address) {
    this.address = address;
    return this;
  }

  /**
   * Sets the retry configuration and returns the modified builder.
   *
   * @param supplier the function to be called by the {@link RetryWithRecovery}
   *                 which returns the desired object and which may fail.
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> supplier(SupplierWithApiException<T> supplier) {
    this.supplier = supplier;
    return this;
  }

  /**
   * Sets the conditions on which we should retry the call to the provided {@link #supplier}.
   *
   * @param retryOnExceptionPredicate the condition when we should retry the call
   *                                  when the {@link #supplier} throws an exception.
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> retryOnException(Predicate<Throwable> retryOnExceptionPredicate) {
    this.retryOnExceptionPredicate = retryOnExceptionPredicate;
    return this;
  }

  /**
   * Sets the condition on which we should ignore an {@link ApiException} thrown by the {@link #supplier}
   * and return null in {@link RetryWithRecovery#execute()}.
   *
   * @param ignoreException the condition when we should ignore a given exception
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> ignoreException(Predicate<ApiException> ignoreException) {
    this.ignoreException = (e) -> e instanceof ApiException && ignoreException.test((ApiException) e);
    return this;
  }

  /**
   * Sets one recovery strategy which consists of a predicate on a thrown {@link ApiException}
   * and of a corresponding recovery function to be executed when condition is met.
   *
   * @param condition the predicate to check if the exception should lead to the execution of the recovery function.
   * @param recovery  the recovery function to be executed when condition is fulfilled.
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> recoveryStrategy(Predicate<ApiException> condition,
      ConsumerWithThrowable recovery) {
    this.recoveryStrategies.add(new RecoveryStrategy(ApiException.class, condition, recovery));
    return this;
  }

  /**
   * Sets one recovery strategy which consists of a specific {@link Exception} type
   * and of a corresponding recovery function to be executed when exception is of the given provided type.
   *
   * @param exceptionType the actual exception class
   * @param recovery      the recovery function to be executed when condition is fulfilled.
   * @param <E>           the actual exception class
   * @return the modified builder instance.
   */
  public <E extends Exception> RetryWithRecoveryBuilder<T> recoveryStrategy(Class<? extends E> exceptionType,
      ConsumerWithThrowable recovery) {
    this.recoveryStrategies.add(new RecoveryStrategy(exceptionType, e -> true, recovery));
    return this;
  }

  /**
   * Removes all the recovery strategies from the builder instance.
   *
   * @return the modified builder instance.
   */
  public RetryWithRecoveryBuilder<T> clearRecoveryStrategies() {
    this.recoveryStrategies.clear();
    return this;
  }

  /**
   * Builds a {@link RetryWithRecovery} based on provided fields.
   *
   * @return a new instance of {@link RetryWithRecovery} based on the provided fields.
   */
  public RetryWithRecovery<T> build() {
    return new Resilience4jRetryWithRecovery<>(name, address, retryConfig, supplier, retryOnExceptionPredicate,
        ignoreException,
        recoveryStrategies);
  }
}
